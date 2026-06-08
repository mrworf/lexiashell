# Architecture Review: LexiaShell

## Scope

This review covers the native Android shell in `/home/ha/projects/lexiashell` as of June 8, 2026. I reviewed the Gradle Android project structure, manifest, `MainActivity`, supporting policy/helper classes, resource configuration, tooling bootstrap script, and JVM unit tests.

The review is architectural rather than a security-only or line-by-line code review. Device and emulator behavior was considered only from source because project instructions state that default verification is build-only.

## Executive Summary

LexiaShell is appropriately small for its stated job: a fullscreen native Android WebView shell for `https://www.lexiacore5.com`. The project has avoided unnecessary app chrome, screens, navigation controls, and additional UI frameworks, which keeps the design aligned with `AGENTS.md`.

Good: the code extracts several important decisions into plain Kotlin policy objects with focused unit tests. `CspNavigationPolicy`, `CspPolicyFetcher`, `LockTaskStartPolicy`, `GameStateReportPolicy`, `CustomViewSession`, and `JavaScriptConsoleLog` keep much of the high-risk logic out of direct Android lifecycle code.

Risky: the app now depends on live CSP fetching to decide which main-frame hosts may load, and the failure mode is bootstrap-origin-only. That is conservative from a containment perspective, but operationally brittle because a transient network, HTTP, redirect, or server behavior problem can break authentication or help flows before the user sees the page.

Risky: `MainActivity` owns WebView setup, fullscreen behavior, lock task, GameManager reporting, CSP fetch orchestration, logging, lifecycle cleanup, and custom fullscreen views in one class. This is still acceptable at the current size, but it is the primary place future changes will become hard to reason about.

The project builds, lints, and passes unit tests with the project-local toolchain command:

```sh
GRADLE_USER_HOME=$PWD/.gradle JAVA_HOME=$PWD/.jdk/temurin-17 ./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

## What Is Good

Good: the app boundary is narrow and clear. The Android namespace and application id are both `nu.sensenet.lexiashell` in `app/build.gradle.kts:6` and `app/build.gradle.kts:15`, and the manifest exposes only a launcher activity plus internet access in `app/src/main/AndroidManifest.xml:3` and `app/src/main/AndroidManifest.xml:15`. This fits the "one behavior" project rule.

Good: the WebView is configured for a desktop-style Core5 experience without adding application-level UI. `MainActivity` creates a single fullscreen `WebView`, disables long-click behavior, enables JavaScript and storage, uses a desktop user agent, and loads only the Lexia URL after policy fetch in `app/src/main/java/nu/sensenet/lexiashell/MainActivity.kt:50`, `app/src/main/java/nu/sensenet/lexiashell/MainActivity.kt:284`, and `app/src/main/java/nu/sensenet/lexiashell/MainActivity.kt:308`.

Good: policy logic is testable outside Android runtime. `CspNavigationPolicy` validates schemes, normalizes hosts, separates exact host and wildcard domain handling, and returns explainable decisions in `app/src/main/java/nu/sensenet/lexiashell/CspNavigationPolicy.kt:12`. Unit tests cover allowed hosts, blocked schemes, malformed URLs, wildcard parent mismatch, and missing CSP fallback in `app/src/test/java/nu/sensenet/lexiashell/CspNavigationPolicyTest.kt:10`.

Good: the CSP fetcher has a dependency-injected `CspHeaderSource`, which makes network behavior testable without live HTTP. The tests cover HEAD success, GET fallback, exceptions, and missing headers in `app/src/test/java/nu/sensenet/lexiashell/CspPolicyFetcherTest.kt:9`.

Good: Android-specific affordances are kept minimal and purposeful. Lock task starts only when permitted and the keyguard is unlocked in `app/src/main/java/nu/sensenet/lexiashell/MainActivity.kt:240`, with the pure decision covered by `app/src/test/java/nu/sensenet/lexiashell/LockTaskStartPolicyTest.kt:9`. Game state reporting is isolated behind SDK gating in `app/src/main/java/nu/sensenet/lexiashell/GameStateReportPolicy.kt:3`.

Good: project-local tooling is documented and mostly implemented. `.gitignore` excludes `.android-sdk`, `.jdk`, `.tools`, `.gradle`, and `local.properties` in `.gitignore:2`, and `scripts/bootstrap_android_tooling.sh:4` installs Android command-line tools, a JDK, and Gradle under the repository.

## What Is Bad Or Risky

Risky: CSP-driven navigation can fail closed in a way that may look like an application outage. `loadLexiaAfterPolicyFetch` waits for `CspPolicyFetcher.fetch` before loading the WebView in `app/src/main/java/nu/sensenet/lexiashell/MainActivity.kt:308`. If both HEAD and GET fail or no CSP header is visible, `CspPolicyFetcher` returns a bootstrap-origin-only policy in `app/src/main/java/nu/sensenet/lexiashell/CspPolicyFetcher.kt:16`. Tests confirm that `auth.mylexia.com` is then blocked in `app/src/test/java/nu/sensenet/lexiashell/CspPolicyFetcherTest.kt:55`. This is secure by default, but operationally fragile for a login-dependent WebView shell.

Risky: the CSP parser treats sources from every directive as navigation allowlist inputs. `CspNavigationPolicy.fromCsp` splits all directives and consumes every host source in `app/src/main/java/nu/sensenet/lexiashell/CspNavigationPolicy.kt:60`. That intentionally allows hosts found in `default-src` and `connect-src`, as demonstrated in `app/src/test/java/nu/sensenet/lexiashell/CspNavigationPolicyTest.kt:10`. The risk is semantic drift: CSP directives describe resource loading categories, not necessarily top-level navigation permission. If Lexia adds a broad analytics or asset host to any directive, main-frame navigation to that host may become allowed.

Risky: lifecycle and threading are manually coordinated. `loadLexiaAfterPolicyFetch` creates a raw `Thread`, checks a volatile `isDestroyed`, and then posts to the UI thread in `app/src/main/java/nu/sensenet/lexiashell/MainActivity.kt:308`. This is workable today, but raw thread lifecycle handling is easy to extend incorrectly if more startup work is added.

Risky: `MainActivity` is the only runtime composition point. It directly constructs `CspPolicyFetcher`, configures `WebSettings`, installs `WebViewClient` and `WebChromeClient`, handles fullscreen, custom views, lock task, and game state reporting. The current class length is reasonable, but the dependency direction means future policy or startup changes will probably require editing lifecycle code in `app/src/main/java/nu/sensenet/lexiashell/MainActivity.kt:32`.

Risky: runtime observability exists only through Android logs. JavaScript console output, navigation decisions, load errors, and CSP fetch failures are logged through `AndroidLexiaLogger` in `app/src/main/java/nu/sensenet/lexiashell/LexiaLogger.kt:10`, but there is no user-visible fallback or local diagnostic screen. That matches the no-extra-UX rule, but it means production troubleshooting depends on adb/logcat access.

Risky: build versions are very current and local-tooling-dependent. The app uses Android Gradle Plugin `9.1.1` in `build.gradle.kts:2`, Gradle `9.3.1` in `gradle/wrapper/gradle-wrapper.properties:3`, and compile/target SDK release 37 in `app/build.gradle.kts:8`. This is acceptable if the goal is tracking modern Android, but it raises reproducibility pressure because contributors must have the matching local SDK installed by `scripts/bootstrap_android_tooling.sh:53`.

## What Should Change

Change: add a small, explicit fallback allowlist for known Lexia authentication domains, or persist the last known good CSP-derived policy after a successful fetch. The current fallback is secure but brittle; a temporary failure in CSP discovery should not necessarily prevent known first-party login/navigation hosts from working. Keep the allowlist narrow and documented, and cover it with unit tests around `CspPolicyFetcher`.

Change: narrow the CSP-derived navigation policy to directives that are defensible for top-level navigation, or document the intentional broader interpretation in code and tests. If the broad behavior is intentional because Core5 uses resource directives to reveal necessary domains, keep it but rename the concept from strict "CSP navigation policy" to something like "CSP host allowlist" so future maintainers do not assume browser-equivalent CSP semantics.

Change: extract startup policy loading from `MainActivity` only when the next startup behavior is added. A small `LexiaStartupLoader` or similar object could own "fetch policy, handle fallback, then provide URL plus policy" while `MainActivity` remains lifecycle/UI composition. This is not urgent, but it is the first extraction I would make before adding retries, cached policies, telemetry, or environment-specific URLs.

Change: add one instrumentation or Robolectric-level test only if runtime regressions become likely. The pure unit tests are strong for policy classes, but they cannot catch broken WebView settings, manifest activity config, custom view `setContentView` behavior, or system UI behavior. Given the project instructions, build-only validation is enough today; add runtime tests when lifecycle behavior starts changing more often.

Change: ignore or remove generated local logs from the working tree. `log.txt` is currently untracked and about 35 MB. It is not an architecture defect, but large local diagnostic files can accidentally become review noise or commit risk.

## What I Would Not Change Yet

Do not change yet: do not introduce a larger Android architecture framework, dependency injection framework, or multi-module structure. The app has one screen and one domain behavior; adding framework machinery would cost more than it returns right now.

Do not change yet: do not split every `MainActivity` helper into separate classes immediately. The current helper extraction already covers the most testable policy decisions. More splitting should be driven by a concrete new behavior such as cached CSP policy, retry strategy, or richer diagnostics.

Do not change yet: do not remove third-party cookie support without product testing. `CookieManager.setAcceptThirdPartyCookies` is explicitly justified for MyLexia authentication in `app/src/main/java/nu/sensenet/lexiashell/MainActivity.kt:300`. It increases privacy surface area, but disabling it would likely break the core login flow.

Do not change yet: do not add app chrome, settings, or user-facing diagnostics unless explicitly requested. The lack of fallback UI makes operations harder, but the project goal is a locked-down fullscreen shell, and visible app controls would work against that goal.

## Overall Opinion

LexiaShell is architecturally sound for a minimal kiosk-like Android WebView wrapper. Its strongest design choice is restraint: one activity, no extra UI framework, project-local tooling, and plain Kotlin policy objects with useful unit tests.

The main thing to watch is the policy-fetch startup path. It is a thoughtful attempt to avoid a hard-coded host allowlist, but it makes live server header behavior part of application startup and main-frame navigation. I would make that path more explicit and resilient before adding any broader features.

Overall recommendation: preserve the current small shape, harden the CSP/navigation fallback semantics, and defer broader refactoring until the app grows beyond the current single-purpose shell.

## Verification

The first Gradle attempt failed inside the sandbox because Gradle could not create its file-lock contention service: `Could not determine a usable wildcard IP for this machine`.

Verification passed outside the sandbox with the project-local Gradle and JDK configuration:

```sh
GRADLE_USER_HOME=$PWD/.gradle JAVA_HOME=$PWD/.jdk/temurin-17 ./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

Result: `BUILD SUCCESSFUL`.

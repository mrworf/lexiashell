# Security Review Report

## Metadata

- **Project/repository:** LexiaShell
- **Git SHA:** `3c292e89c7ef8409149522d2cbbb9e08e4782c3b`
- **Review date/time:** `2026-06-09T03:51:05Z`
- **Reviewer role:** senior application security reviewer
- **Scope reviewed:** Android app source, manifest, Gradle build files, wrapper configuration, unit tests, and project documentation in this repository.
- **Commands run:** `git rev-parse HEAD`; `date -u +"%Y-%m-%dT%H:%M:%SZ"`; `git status --short`; `rg --files`; targeted `sed`/`nl` file reads; `rg` security keyword search; `GRADLE_USER_HOME=$PWD/.gradle JAVA_HOME=$PWD/.jdk/temurin-17 ./gradlew :app:testDebugUnitTest`; `GRADLE_USER_HOME=$PWD/.gradle JAVA_HOME=$PWD/.jdk/temurin-17 ./gradlew :app:dependencies --configuration debugRuntimeClasspath`; `GRADLE_USER_HOME=$PWD/.gradle JAVA_HOME=$PWD/.jdk/temurin-17 ./gradlew :app:assembleDebug :app:lintDebug`.
- **External references checked:** Mozilla advisory MFSA 2026-46, Mozilla advisory MFSA 2026-54, Maven metadata search for GeckoView versions.
- **Assumptions and limitations:** This was a source review plus local build/lint/unit-test validation. No emulator/device runtime test, dynamic traffic capture, APK reverse engineering, Play release review, or signed release artifact review was performed.

## Executive Summary

LexiaShell has a small and generally well-contained security surface: it is a single-purpose Android GeckoView shell for `https://www.lexiacore5.com`. I did not find a confirmed app-code vulnerability that would let another Android app or arbitrary website take over the shell, bypass navigation controls, or obtain Android runtime permissions.

The main security risk is dependency freshness: the app embeds a full browser engine, `org.mozilla.geckoview:geckoview:151.0.20260601110758`, and Mozilla published high-impact Firefox 151.0.3 fixes on June 2, 2026, after the pinned timestamp. That does not prove this exact GeckoView Maven artifact is affected, but it is the most important item to validate because browser-engine bugs can be reachable through web content.

The strongest positive controls are: only the Internet permission is requested, Android backup is disabled, runtime/media permissions are rejected by default, new windows are blocked, and main-frame navigation is restricted to HTTPS hosts derived from the Lexia bootstrap origin and CSP.

## Scope and Methodology

Reviewed code and configuration included:

- App manifest and resources under `app/src/main`.
- Kotlin application code under `app/src/main/java/nu/sensenet/lexiashell`.
- Unit tests under `app/src/test`.
- Gradle build, dependency, repository, and wrapper configuration.
- Existing audit documentation and README/AGENTS project guidance.

The review mapped entry points and trust boundaries first, then inspected navigation policy, network policy fetching, GeckoView settings/delegates, Android permissions, exported components, logging, dependency declarations, and tests. Findings are evidence-based and separated from hardening observations.

## Threat Model

- **Exposed interfaces:** Android launcher activity, embedded GeckoView web content, outbound HTTPS fetch to Lexia, Android lock-task/game APIs.
- **Sensitive assets:** Lexia learner session state inside GeckoView storage, authenticated Lexia web content, browser process integrity, app availability during lessons.
- **Trust boundaries:** Android app process vs. web content; Lexia allowed origins vs. arbitrary external origins; Android app vs. other installed apps; app build/dependency supply chain vs. runtime.
- **Likely attacker profiles:** Malicious or compromised web content on an allowed Lexia/CSP host, network attacker unable to break TLS, malicious installed Android app that can launch exported activities, dependency/supply-chain actor.

## Findings Summary

| ID | Severity | CVSS | Confidence | Title | Status |
|----|----------|------|------------|-------|--------|
| SEC-001 | High | 7.5 | Medium | Embedded GeckoView may lag current Firefox 151 security fixes | Needs validation |

## Detailed Findings

### SEC-001: Embedded GeckoView May Lag Current Firefox 151 Security Fixes

- **Severity:** High
- **CVSS v3.1:** 7.5 `CVSS:3.1/AV:N/AC:H/PR:N/UI:R/S:U/C:H/I:H/A:H`
- **Confidence:** Medium
- **Status:** Needs validation
- **Affected components:** `app/build.gradle.kts`; GeckoView runtime dependency

#### Evidence

The app pins GeckoView to a timestamped Firefox 151 build:

- `app/build.gradle.kts:8` defines `val geckoViewVersion = "151.0.20260601110758"`.
- `app/build.gradle.kts:89` includes `implementation("org.mozilla.geckoview:geckoview:$geckoViewVersion")`.
- The Gradle dependency report confirmed `org.mozilla.geckoview:geckoview:151.0.20260601110758` on `debugRuntimeClasspath`.

Mozilla advisory MFSA 2026-54, announced June 2, 2026, states that Firefox 151.0.3 fixed high-impact issues including `CVE-2026-10701` in Graphics: Text and `CVE-2026-10702` in the JavaScript JIT. See: https://www.mozilla.org/en-US/security/advisories/mfsa2026-54/

Mozilla advisory MFSA 2026-46, announced May 19, 2026, lists the broader Firefox 151 security baseline, including Android-relevant sandbox and browser-engine issues. See: https://www.mozilla.org/en-US/security/advisories/mfsa2026-46/

#### Preconditions

- The app embeds GeckoView and executes JavaScript for Lexia content (`GeckoSessionSettingsPolicy.kt:8`, `:17`).
- An attacker must cause the embedded browser to process malicious content reachable through the app's allowed navigation/content surface. In this app, main-frame navigation is restricted, so the realistic paths are compromise of an allowed Lexia/CSP host, a vulnerable third-party resource allowed by Lexia content, or another trusted-content supply-chain failure.
- Runtime exploitability depends on whether Mozilla's Firefox 151.0.3 fixes map to a newer GeckoView artifact available for this app's release channel.

#### Exploit Scenario

A user launches LexiaShell and the app loads Lexia in GeckoView. If the pinned GeckoView build is missing a browser-engine security fix and an attacker can control content served from an allowed origin or allowed resource path, the attacker may be able to trigger a browser-engine memory corruption or JIT bug inside the GeckoView content process. Depending on the underlying vulnerability and Gecko sandboxing, this could affect confidentiality, integrity, and availability of the web session or app process.

This is not a finding that arbitrary external websites are reachable: the app has a main-frame HTTPS host allowlist. The risk is specifically that embedded browser engines are high-value dependencies and must track security patch releases closely.

#### Safe PoC / Validation

Non-destructive validation:

1. Query Mozilla's Maven repository for GeckoView artifacts newer than `151.0.20260601110758` in the desired release channel.
2. Confirm from Mozilla release notes or artifact metadata whether any newer GeckoView build contains Firefox 151.0.3 security fixes.
3. Update the local `geckoViewVersion` on a branch and run:
   - `GRADLE_USER_HOME=$PWD/.gradle JAVA_HOME=$PWD/.jdk/temurin-17 ./gradlew :app:testDebugUnitTest`
   - `GRADLE_USER_HOME=$PWD/.gradle JAVA_HOME=$PWD/.jdk/temurin-17 ./gradlew :app:assembleDebug :app:lintDebug`
4. Perform a basic device smoke test of Lexia load/login if device testing is in scope.

This PoC is non-destructive because it validates dependency metadata and local build compatibility only.

#### Impact

Browser-engine vulnerabilities can have high impact when reachable through web content: session compromise, arbitrary script effects beyond web isolation, content-process crash, or worse if a sandbox escape is involved. The app's narrow navigation policy reduces attacker reachability but does not eliminate trusted-origin or dependency-supply-chain exposure.

#### CVSS Rationale

`AV:N` because browser content is network-delivered. `AC:H` because exploitation requires content control within the app's restricted allowed-origin surface and a matching vulnerable browser-engine path. `PR:N` because no app privilege is needed beyond delivering content. `UI:R` because a user must launch/use the app. `S:U` is used conservatively because source review did not confirm a sandbox escape in this exact GeckoView artifact. `C:H/I:H/A:H` reflects potential browser-engine compromise impact if the dependency is affected.

#### Remediation

- Track GeckoView security releases explicitly, not only functional releases.
- Update `geckoViewVersion` to a build that Mozilla identifies as containing the Firefox 151.0.3 security fixes, or to the current stable GeckoView release appropriate for the app.
- Add a recurring dependency review step before release builds.
- Consider documenting the expected GeckoView update cadence in `AGENTS.md` or release notes.

#### Verification

- Dependency report shows the updated GeckoView artifact.
- Unit, build, and lint validation pass.
- Device smoke test confirms Lexia still loads and stays fullscreen.
- Release notes or Mozilla advisory mapping confirms the embedded engine includes the relevant security fixes.

## Exploit Chains

No concrete exploit chain was confirmed from the reviewed source. A plausible but unconfirmed chain is: allowed-origin web content compromise plus vulnerable embedded GeckoView build. That chain is covered by SEC-001 and needs dependency/version validation.

## Hardening Recommendations

1. **Automate browser-engine dependency monitoring.** GeckoView is effectively the app's runtime security boundary for web content. Add a release checklist item or dependency-update automation that alerts on Mozilla security advisories affecting the pinned engine.
2. **Consider enabling tracking protection if Lexia compatibility permits.** `GeckoSessionSettingsPolicy.kt:12` sets `useTrackingProtection = false`. This is not a vulnerability for a single-purpose education app, but enabling it would reduce exposure to third-party tracking/resource abuse if Lexia works with it.
3. **Consider a Network Security Config with `cleartextTrafficPermitted="false"`.** The app already uses HTTPS-only navigation policy and has no cleartext manifest opt-in. A config would make the intended posture explicit and easier to audit.
4. **Consider release-signing review.** This source tree does not include release signing configuration. Before distributing release APKs/AABs, verify keystore protection, signing lineage, and Play/App Store delivery controls.

## Positive Security Observations

- The manifest requests only `android.permission.INTERNET` and disables Android backup (`AndroidManifest.xml:3`, `:6`).
- The launcher activity is exported only for the launcher intent and does not process external URL/deep-link input (`AndroidManifest.xml:27-36`).
- Main-frame navigation is HTTPS-only and host-restricted (`CspNavigationPolicy.kt:18-50`).
- The allowlist starts from the bootstrap host and only expands based on parsed CSP host sources (`CspNavigationPolicy.kt:55-80`).
- The CSP fetcher uses short connect/read timeouts (`CspPolicyFetcher.kt:90-100`) and treats transport failure as a load failure in `MainActivity`.
- New-window sessions are blocked (`MainActivity.kt:162-167`, `:181-186`).
- Android runtime permission requests and media permission requests from web content are rejected (`MainActivity.kt:290-315`).
- Content permission policy only allows autoplay and storage access; geolocation and notifications are denied by default (`GeckoContentPermissionPolicy.kt:6-13`).
- Gradle repositories are centralized with `RepositoriesMode.FAIL_ON_PROJECT_REPOS` (`settings.gradle.kts:9-17`).
- Gradle wrapper uses HTTPS and validates the distribution URL (`gradle-wrapper.properties:3-5`).
- Unit tests cover the navigation allowlist, CSP fallback behavior, permission policy, session settings, lock task policy, and loading recovery behavior.

## Assumptions and Limitations

- I did not test the app on a physical Android device or emulator.
- I did not intercept live Lexia traffic or validate the current production CSP returned by `https://www.lexiacore5.com`.
- I did not reverse engineer generated APK contents or inspect release signing.
- Mozilla's public advisory pages identify Firefox product versions; mapping those fixes to the exact GeckoView Maven timestamp should be validated before treating SEC-001 as confirmed.
- No secrets scanning beyond source/config keyword search was performed against ignored local caches or binary artifacts.

## Appendix

### Command Results Summary

- `git rev-parse HEAD`: `3c292e89c7ef8409149522d2cbbb9e08e4782c3b`
- `date -u +"%Y-%m-%dT%H:%M:%SZ"`: `2026-06-09T03:51:05Z`
- `git status --short`: clean at review start
- `:app:testDebugUnitTest`: passed
- `:app:dependencies --configuration debugRuntimeClasspath`: passed; confirmed GeckoView `151.0.20260601110758`
- `:app:assembleDebug :app:lintDebug`: passed

### Files Most Relevant to Review

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/nu/sensenet/lexiashell/MainActivity.kt`
- `app/src/main/java/nu/sensenet/lexiashell/CspNavigationPolicy.kt`
- `app/src/main/java/nu/sensenet/lexiashell/CspPolicyFetcher.kt`
- `app/src/main/java/nu/sensenet/lexiashell/GeckoContentPermissionPolicy.kt`
- `app/src/main/java/nu/sensenet/lexiashell/GeckoSessionSettingsPolicy.kt`
- `app/build.gradle.kts`
- `settings.gradle.kts`
- `gradle/wrapper/gradle-wrapper.properties`

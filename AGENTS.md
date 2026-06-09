# Project Instructions

This project is a minimal native Android shell for Lexia Core5.

## Implementation Rules

- Keep Android SDK, JDK, Gradle, and other downloaded build tooling project-local when possible.
- Run Gradle with `GRADLE_USER_HOME=$PWD/.gradle` so wrapper distributions and caches stay project-local.
- GeckoView intentionally uses the stable `org.mozilla.geckoview:geckoview` artifact with `latest.release`; validation/release dependency reports and startup logs must confirm the concrete resolved GeckoView version.
- Build the app as a native Kotlin Android application with no additional UI framework.
- Use `nu.sensenet.lexiashell` for both the Android namespace and application id.
- Keep the app focused on one behavior: a fullscreen desktop-mode GeckoView for `https://www.lexiacore5.com`.
- Do not add app chrome, navigation controls, settings screens, or extra UX unless explicitly requested.
- Device and emulator testing are out of scope unless explicitly requested; default verification is build-only.

## Validation

After the Android scaffold exists, the canonical validation command is:

```sh
GRADLE_USER_HOME=$PWD/.gradle JAVA_HOME=$PWD/.jdk/temurin-17 ./gradlew :app:assembleDebug :app:lintDebug
```

In the Codex sandbox, Gradle may fail before compilation with `Could not determine a usable wildcard IP for this machine` while creating its file-lock contention service. When running Gradle validation from Codex, request escalation for the Gradle command up front instead of first retrying inside the sandbox.

Release builds use `:app:assembleRelease` for an arm64-v8a sideload APK and `:app:bundleRelease` for the Google Play App Bundle.

For slices before Gradle exists, validate with file inspection and `git status`.

## Slice Workflow

- Keep each slice limited to one useful behavior change.
- Run the canonical validation available for the current slice before committing.
- Commit each completed slice with a concise imperative subject.
- Update this file only for durable lessons or project rules, not for one-off troubleshooting notes.

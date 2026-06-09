# Lexia Shell

Lexia Shell is a tiny native Android kiosk application for [Lexia Core5](https://www.lexiacore5.com). It is built for one job: open Core5 fullscreen, in desktop mode, and stay out of the way.

Why does this exist? Because there is no current Lexia Core5 Android app. 

During development, I found the likely reason.Chrome/WebView seems to leak file descriptors that make the embedded browser crash after a few minutes. 

Lexia Shell uses GeckoView instead, which is Firefox-based. It is a bit less speedy in places, but it is absolutely good enough for a solid classroom-tablet experience.

This is not a general-purpose browser. There are no tabs, no navigation controls, no settings screen, and no tracking. The app exists to load `https://www.lexiacore5.com` and keep the lesson surface clean.

## Trust, Build, Verify

The code is 100% open source. 

Full disclosure: it was created with AI assistance with the supervision, direction, and design. That does not make it magic, but it does make it inspectable. Security and architecture reviews are kept under `audit/`, and the app does not log user data. 

I have no interest in adding telemetry.

If you want extra confidence, you can build it yourself. The app embeds build provenance such as the source commit, build timestamp, and whether tracked source files were modified, so you can tell what you are running.

## Building

The project keeps Android, Java, and Gradle tooling local where practical. After the scaffold/tooling is present, the canonical debug validation command is:

```sh
GRADLE_USER_HOME=$PWD/.gradle JAVA_HOME=$PWD/.jdk/temurin-17 ./gradlew :app:assembleDebug :app:lintDebug
```

Release builds use:

```sh
GRADLE_USER_HOME=$PWD/.gradle JAVA_HOME=$PWD/.jdk/temurin-17 ./gradlew :app:assembleRelease
```

For a Google Play App Bundle:

```sh
GRADLE_USER_HOME=$PWD/.gradle JAVA_HOME=$PWD/.jdk/temurin-17 ./gradlew :app:bundleRelease
```

## Support

Support is not guaranteed. That said, this app was made because my own kid needs Core5 on Android, so I have a very good reason to keep it working.

Pull requests are welcome if something bothers you, breaks, or can be made simpler.

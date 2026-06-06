#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOWNLOAD_DIR="$ROOT_DIR/.tools/downloads"
ANDROID_SDK_ROOT="$ROOT_DIR/.android-sdk"
JDK_DIR="$ROOT_DIR/.jdk/temurin-17"
GRADLE_DIR="$ROOT_DIR/.tools/gradle-9.3.1"

CMDLINE_TOOLS_URL="${CMDLINE_TOOLS_URL:-https://dl.google.com/android/repository/commandlinetools-linux-14742923_latest.zip}"
JDK_URL="${JDK_URL:-https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse?project=jdk}"
GRADLE_URL="${GRADLE_URL:-https://services.gradle.org/distributions/gradle-9.3.1-bin.zip}"

mkdir -p "$DOWNLOAD_DIR" "$ANDROID_SDK_ROOT/cmdline-tools" "$ROOT_DIR/.jdk" "$ROOT_DIR/.tools"

download() {
    local url="$1"
    local target="$2"
    if [[ ! -f "$target" ]]; then
        curl -fsSL "$url" -o "$target"
    fi
}

if [[ ! -x "$JDK_DIR/bin/java" ]]; then
    rm -rf "$JDK_DIR"
    mkdir -p "$JDK_DIR"
    download "$JDK_URL" "$DOWNLOAD_DIR/jdk17.tar.gz"
    tar -xzf "$DOWNLOAD_DIR/jdk17.tar.gz" -C "$JDK_DIR" --strip-components=1
fi

if [[ ! -x "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]]; then
    rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/latest" "$ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools"
    download "$CMDLINE_TOOLS_URL" "$DOWNLOAD_DIR/android-commandlinetools.zip"
    unzip -q "$DOWNLOAD_DIR/android-commandlinetools.zip" -d "$ANDROID_SDK_ROOT/cmdline-tools"
    mv "$ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
fi

if [[ ! -x "$GRADLE_DIR/bin/gradle" ]]; then
    rm -rf "$GRADLE_DIR" "$ROOT_DIR/.tools/gradle-9.3.1-tmp"
    download "$GRADLE_URL" "$DOWNLOAD_DIR/gradle-9.3.1-bin.zip"
    unzip -q "$DOWNLOAD_DIR/gradle-9.3.1-bin.zip" -d "$ROOT_DIR/.tools"
fi

export JAVA_HOME="$JDK_DIR"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export ANDROID_SDK_ROOT
export GRADLE_USER_HOME="$ROOT_DIR/.gradle"
export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$JAVA_HOME/bin:$PATH"

set +o pipefail
yes | sdkmanager --licenses >/dev/null
set -o pipefail
sdkmanager \
    "platform-tools" \
    "build-tools;36.0.0" \
    "platforms;android-37.0"

cat > "$ROOT_DIR/local.properties" <<EOF
sdk.dir=$ANDROID_SDK_ROOT
org.gradle.java.home=$JDK_DIR
EOF

if [[ ! -f "$ROOT_DIR/gradlew" ]]; then
    "$GRADLE_DIR/bin/gradle" --project-dir "$ROOT_DIR" wrapper --gradle-version 9.3.1
fi

echo "Android tooling is ready."
echo "Run builds with: JAVA_HOME=$JDK_DIR ./gradlew :app:assembleDebug"

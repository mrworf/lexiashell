import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("com.android.application")
}

val geckoViewVersion = "151.0.20260601110758"

fun gitOutput(vararg args: String): String =
    providers.exec {
        commandLine("git", *args)
        workingDir = rootDir
    }.standardOutput.asText.get().trim()

fun quotedBuildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "nu.sensenet.lexiashell"

    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "nu.sensenet.lexiashell"

        minSdk {
            version = release(26)
        }

        targetSdk {
            version = release(37)
        }

        versionCode = 1
        versionName = "1.0"

        buildConfigField(
            "String",
            "SOURCE_COMMIT",
            quotedBuildConfigString(
                runCatching { gitOutput("rev-parse", "--short=12", "HEAD") }.getOrDefault("unknown"),
            ),
        )
        buildConfigField(
            "boolean",
            "SOURCE_TRACKED_DIRTY",
            runCatching {
                gitOutput("status", "--porcelain=v1", "--untracked-files=no").isNotEmpty()
            }.getOrDefault(true).toString(),
        )
        buildConfigField(
            "String",
            "BUILD_TIMESTAMP",
            quotedBuildConfigString(DateTimeFormatter.ISO_ZONED_DATE_TIME.format(ZonedDateTime.now())),
        )
        buildConfigField(
            "String",
            "GECKOVIEW_VERSION",
            quotedBuildConfigString(geckoViewVersion),
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("org.mozilla.geckoview:geckoview:$geckoViewVersion")

    testImplementation("junit:junit:4.13.2")
}

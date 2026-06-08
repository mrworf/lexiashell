import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("com.android.application")
}

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
            version = release(21)
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
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

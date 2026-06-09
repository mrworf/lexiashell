import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

plugins {
    id("com.android.application")
}

val geckoViewVersionSelector = "latest.release"

configurations.configureEach {
    resolutionStrategy.cacheDynamicVersionsFor(0, TimeUnit.SECONDS)
}

fun gitOutput(vararg args: String): String =
    providers.exec {
        commandLine("git", *args)
        workingDir = rootDir
    }.standardOutput.asText.get().trim()

fun quotedBuildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

fun resolvedGeckoViewVersion(): String {
    val configuration = configurations.detachedConfiguration(
        dependencies.create("org.mozilla.geckoview:geckoview:$geckoViewVersionSelector"),
    ).apply {
        isTransitive = false
        resolutionStrategy.cacheDynamicVersionsFor(0, TimeUnit.SECONDS)
    }
    return configuration
        .incoming
        .resolutionResult
        .allComponents
        .mapNotNull { it.moduleVersion }
        .first {
            it.group == "org.mozilla.geckoview" &&
                it.name == "geckoview"
        }
        .version
}

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

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

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
            quotedBuildConfigString(resolvedGeckoViewVersion()),
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
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core:1.19.0")
    implementation("org.mozilla.geckoview:geckoview:$geckoViewVersionSelector")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}

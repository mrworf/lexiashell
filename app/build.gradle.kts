plugins {
    id("com.android.application")
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
            version = release(21)
        }

        targetSdk {
            version = release(37)
        }

        versionCode = 1
        versionName = "1.0"
    }
}

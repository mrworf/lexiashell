package nu.sensenet.lexiashell

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildProvenanceTest {
    @Test
    fun formatsCleanBuildMetadata() {
        assertEquals(
            "Startup identity: appVersion=1.2.3 versionCode=45 " +
                "commit=abc123def456 trackedDirty=false " +
                "builtAt=2026-06-08T12:34:56-07:00[America/Los_Angeles] " +
                "geckoViewVersion=152.0.1",
            BuildProvenance.startupLogLine(
                appVersionName = "1.2.3",
                appVersionCode = 45,
                sourceCommit = "abc123def456",
                sourceTrackedDirty = false,
                buildTimestamp = "2026-06-08T12:34:56-07:00[America/Los_Angeles]",
                geckoViewVersion = "152.0.1",
            ),
        )
    }

    @Test
    fun formatsDirtyBuildMetadata() {
        assertEquals(
            "Startup identity: appVersion=1.2.3 versionCode=45 " +
                "commit=abc123def456 trackedDirty=true " +
                "builtAt=2026-06-08T12:34:56-07:00[America/Los_Angeles] " +
                "geckoViewVersion=152.0.1",
            BuildProvenance.startupLogLine(
                appVersionName = "1.2.3",
                appVersionCode = 45,
                sourceCommit = "abc123def456",
                sourceTrackedDirty = true,
                buildTimestamp = "2026-06-08T12:34:56-07:00[America/Los_Angeles]",
                geckoViewVersion = "152.0.1",
            ),
        )
    }

    @Test
    fun preservesUnknownCommit() {
        assertEquals(
            "Startup identity: appVersion=1.2.3 versionCode=45 " +
                "commit=unknown trackedDirty=false " +
                "builtAt=2026-06-08T12:34:56-07:00[America/Los_Angeles] " +
                "geckoViewVersion=152.0.1",
            BuildProvenance.startupLogLine(
                appVersionName = "1.2.3",
                appVersionCode = 45,
                sourceCommit = "unknown",
                sourceTrackedDirty = false,
                buildTimestamp = "2026-06-08T12:34:56-07:00[America/Los_Angeles]",
                geckoViewVersion = "152.0.1",
            ),
        )
    }
}

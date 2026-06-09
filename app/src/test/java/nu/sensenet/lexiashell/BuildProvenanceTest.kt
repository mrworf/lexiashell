package nu.sensenet.lexiashell

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildProvenanceTest {
    @Test
    fun formatsCleanBuildMetadata() {
        assertEquals(
            "Build provenance: commit=abc123def456 trackedDirty=false " +
                "builtAt=2026-06-08T12:34:56-07:00[America/Los_Angeles] " +
                "geckoViewVersion=152.0.1",
            BuildProvenance.startupLogLine(
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
            "Build provenance: commit=abc123def456 trackedDirty=true " +
                "builtAt=2026-06-08T12:34:56-07:00[America/Los_Angeles] " +
                "geckoViewVersion=152.0.1",
            BuildProvenance.startupLogLine(
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
            "Build provenance: commit=unknown trackedDirty=false " +
                "builtAt=2026-06-08T12:34:56-07:00[America/Los_Angeles] " +
                "geckoViewVersion=152.0.1",
            BuildProvenance.startupLogLine(
                sourceCommit = "unknown",
                sourceTrackedDirty = false,
                buildTimestamp = "2026-06-08T12:34:56-07:00[America/Los_Angeles]",
                geckoViewVersion = "152.0.1",
            ),
        )
    }
}

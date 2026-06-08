package nu.sensenet.lexiashell

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildProvenanceTest {
    @Test
    fun formatsCleanBuildMetadata() {
        assertEquals(
            "Build provenance: commit=abc123def456 trackedDirty=false " +
                "builtAt=2026-06-08T12:34:56-07:00[America/Los_Angeles]",
            BuildProvenance.startupLogLine(
                sourceCommit = "abc123def456",
                sourceTrackedDirty = false,
                buildTimestamp = "2026-06-08T12:34:56-07:00[America/Los_Angeles]",
            ),
        )
    }

    @Test
    fun formatsDirtyBuildMetadata() {
        assertEquals(
            "Build provenance: commit=abc123def456 trackedDirty=true " +
                "builtAt=2026-06-08T12:34:56-07:00[America/Los_Angeles]",
            BuildProvenance.startupLogLine(
                sourceCommit = "abc123def456",
                sourceTrackedDirty = true,
                buildTimestamp = "2026-06-08T12:34:56-07:00[America/Los_Angeles]",
            ),
        )
    }

    @Test
    fun preservesUnknownCommit() {
        assertEquals(
            "Build provenance: commit=unknown trackedDirty=false " +
                "builtAt=2026-06-08T12:34:56-07:00[America/Los_Angeles]",
            BuildProvenance.startupLogLine(
                sourceCommit = "unknown",
                sourceTrackedDirty = false,
                buildTimestamp = "2026-06-08T12:34:56-07:00[America/Los_Angeles]",
            ),
        )
    }
}

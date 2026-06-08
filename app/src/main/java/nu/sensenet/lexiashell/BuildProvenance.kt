package nu.sensenet.lexiashell

object BuildProvenance {
    fun startupLogLine(
        sourceCommit: String = BuildConfig.SOURCE_COMMIT,
        sourceTrackedDirty: Boolean = BuildConfig.SOURCE_TRACKED_DIRTY,
        buildTimestamp: String = BuildConfig.BUILD_TIMESTAMP,
    ): String =
        "Build provenance: " +
            "commit=$sourceCommit " +
            "trackedDirty=$sourceTrackedDirty " +
            "builtAt=$buildTimestamp"
}

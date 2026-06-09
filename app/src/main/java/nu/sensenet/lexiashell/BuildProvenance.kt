package nu.sensenet.lexiashell

object BuildProvenance {
    fun startupLogLine(
        appVersionName: String = BuildConfig.VERSION_NAME,
        appVersionCode: Int = BuildConfig.VERSION_CODE,
        sourceCommit: String = BuildConfig.SOURCE_COMMIT,
        sourceTrackedDirty: Boolean = BuildConfig.SOURCE_TRACKED_DIRTY,
        buildTimestamp: String = BuildConfig.BUILD_TIMESTAMP,
        geckoViewVersion: String = BuildConfig.GECKOVIEW_VERSION,
    ): String =
        "Startup identity: " +
            "appVersion=$appVersionName " +
            "versionCode=$appVersionCode " +
            "commit=$sourceCommit " +
            "trackedDirty=$sourceTrackedDirty " +
            "builtAt=$buildTimestamp " +
            "geckoViewVersion=$geckoViewVersion"
}

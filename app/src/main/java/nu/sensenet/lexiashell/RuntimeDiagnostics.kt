package nu.sensenet.lexiashell

object RuntimeDiagnostics {
    fun gameClassificationLine(
        applicationCategory: Int,
        legacyIsGame: Boolean,
        gameMode: Int?,
    ): String =
        "Game classification: " +
            "appCategory=${applicationCategoryName(applicationCategory)}($applicationCategory) " +
            "legacyIsGame=$legacyIsGame " +
            "gameMode=${gameMode ?: "unavailable"}"

    fun batteryOptimizationLine(isIgnoringBatteryOptimizations: Boolean?): String =
        "Battery optimization: ignoring=${isIgnoringBatteryOptimizations ?: "unsupported"}"

    fun memoryPressureLine(level: Int): String =
        "Memory pressure: level=$level meaning=${trimMemoryLevelName(level)}"

    fun runtimeUptimeLine(
        processElapsedRealtimeMs: Long?,
        activityElapsedRealtimeMs: Long,
    ): String =
        "Runtime uptime: " +
            "processElapsedMs=${processElapsedRealtimeMs ?: "unavailable"} " +
            "activityElapsedMs=$activityElapsedRealtimeMs"

    fun webViewProviderLine(
        packageName: String?,
        versionName: String?,
        versionCode: Long?,
    ): String =
        "WebView provider: " +
            "package=${packageName ?: "unavailable"} " +
            "versionName=${versionName ?: "unavailable"} " +
            "versionCode=${versionCode ?: "unavailable"}"

    fun webViewHardwareAccelerationLine(isHardwareAccelerated: Boolean): String =
        "WebView hardware acceleration: enabled=$isHardwareAccelerated"

    fun webViewLayerTypeLine(layerType: Int): String =
        "WebView layer type: configured=${webViewLayerTypeName(layerType)}($layerType)"

    fun renderProcessGoneLine(didCrash: Boolean, rendererPriorityAtExit: Int): String =
        "WebView render process gone: " +
            "didCrash=$didCrash rendererPriorityAtExit=$rendererPriorityAtExit"

    fun recentExitLine(
        processName: String?,
        reason: Int,
        status: Int,
        importance: Int,
        pssKb: Long,
        rssKb: Long,
        timestampMs: Long,
        description: String?,
    ): String =
        "Recent exit: " +
            "process=${oneLineOrUnavailable(processName)} " +
            "reason=$reason " +
            "status=$status " +
            "importance=$importance " +
            "pssKb=$pssKb " +
            "rssKb=$rssKb " +
            "timestampMs=$timestampMs " +
            "description=${oneLineOrUnavailable(description)}"

    private fun applicationCategoryName(category: Int): String =
        when (category) {
            -1 -> "undefined"
            0 -> "game"
            1 -> "audio"
            2 -> "video"
            3 -> "image"
            4 -> "social"
            5 -> "news"
            6 -> "maps"
            7 -> "productivity"
            8 -> "accessibility"
            else -> "unknown"
        }

    private fun trimMemoryLevelName(level: Int): String =
        when (level) {
            5 -> "running-moderate"
            10 -> "running-low"
            15 -> "running-critical"
            20 -> "ui-hidden"
            40 -> "background"
            60 -> "moderate"
            80 -> "complete"
            else -> "unknown"
        }

    private fun webViewLayerTypeName(layerType: Int): String =
        when (layerType) {
            0 -> "none"
            1 -> "software"
            2 -> "hardware"
            else -> "unknown"
        }

    private fun oneLineOrUnavailable(value: String?): String =
        value
            ?.replace('\n', ' ')
            ?.replace('\r', ' ')
            ?.takeIf { it.isNotBlank() }
            ?: "unavailable"
}

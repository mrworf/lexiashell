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
}

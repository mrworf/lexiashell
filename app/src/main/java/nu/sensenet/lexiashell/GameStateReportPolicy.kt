package nu.sensenet.lexiashell

object GameStateReportPolicy {
    const val MIN_SUPPORTED_SDK = 33

    fun shouldReport(sdkInt: Int): Boolean = sdkInt >= MIN_SUPPORTED_SDK

    fun report(isPageLoading: Boolean): GameStateReport =
        GameStateReport(isLoading = isPageLoading)
}

data class GameStateReport(
    val isLoading: Boolean,
)

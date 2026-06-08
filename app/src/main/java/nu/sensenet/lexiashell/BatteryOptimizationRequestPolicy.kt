package nu.sensenet.lexiashell

object BatteryOptimizationRequestPolicy {
    const val MIN_SUPPORTED_SDK = 23

    fun shouldRequest(
        sdkInt: Int,
        isIgnoringBatteryOptimizations: Boolean,
        hasRequestedBefore: Boolean,
    ): Boolean =
        sdkInt >= MIN_SUPPORTED_SDK &&
            !isIgnoringBatteryOptimizations &&
            !hasRequestedBefore
}

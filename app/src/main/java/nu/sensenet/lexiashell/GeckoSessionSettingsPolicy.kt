package nu.sensenet.lexiashell

import org.mozilla.geckoview.GeckoSessionSettings

object GeckoSessionSettingsPolicy {
    fun values(): GeckoSessionSettingValues =
        GeckoSessionSettingValues(
            allowJavascript = true,
            userAgentMode = GeckoSessionSettings.USER_AGENT_MODE_DESKTOP,
            viewportMode = GeckoSessionSettings.VIEWPORT_MODE_DESKTOP,
            displayMode = GeckoSessionSettings.DISPLAY_MODE_FULLSCREEN,
            useTrackingProtection = false,
        )

    fun applyTo(settings: GeckoSessionSettings) {
        val values = values()
        settings.setAllowJavascript(values.allowJavascript)
        settings.setUserAgentMode(values.userAgentMode)
        settings.setViewportMode(values.viewportMode)
        settings.setDisplayMode(values.displayMode)
        settings.setUseTrackingProtection(values.useTrackingProtection)
    }
}

data class GeckoSessionSettingValues(
    val allowJavascript: Boolean,
    val userAgentMode: Int,
    val viewportMode: Int,
    val displayMode: Int,
    val useTrackingProtection: Boolean,
)

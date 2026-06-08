package nu.sensenet.lexiashell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.geckoview.GeckoSessionSettings

class GeckoSessionSettingsPolicyTest {
    @Test
    fun selectsDesktopFullscreenSettings() {
        val values = GeckoSessionSettingsPolicy.values()

        assertTrue(values.allowJavascript)
        assertEquals(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP, values.userAgentMode)
        assertEquals(GeckoSessionSettings.VIEWPORT_MODE_DESKTOP, values.viewportMode)
        assertEquals(GeckoSessionSettings.DISPLAY_MODE_FULLSCREEN, values.displayMode)
        assertFalse(values.useTrackingProtection)
    }

    @Test
    fun doesNotUseMobileViewportOrUserAgent() {
        val values = GeckoSessionSettingsPolicy.values()

        assertNotEquals(GeckoSessionSettings.USER_AGENT_MODE_MOBILE, values.userAgentMode)
        assertNotEquals(GeckoSessionSettings.VIEWPORT_MODE_MOBILE, values.viewportMode)
    }

    private fun assertNotEquals(unexpected: Int, actual: Int) {
        org.junit.Assert.assertNotEquals(unexpected, actual)
    }
}

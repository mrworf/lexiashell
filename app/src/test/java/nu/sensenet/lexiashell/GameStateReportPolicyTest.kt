package nu.sensenet.lexiashell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStateReportPolicyTest {
    @Test
    fun reportsGameStateOnAndroid13AndNewer() {
        assertTrue(GameStateReportPolicy.shouldReport(sdkInt = 33))
    }

    @Test
    fun doesNotReportGameStateBeforeAndroid13() {
        assertFalse(GameStateReportPolicy.shouldReport(sdkInt = 32))
    }

    @Test
    fun pageLoadingMapsToLoadingGameplayState() {
        assertTrue(GameStateReportPolicy.report(isPageLoading = true).isLoading)
    }

    @Test
    fun visibleIdleMapsToNonLoadingGameplayState() {
        assertFalse(GameStateReportPolicy.report(isPageLoading = false).isLoading)
    }
}

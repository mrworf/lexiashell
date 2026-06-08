package nu.sensenet.lexiashell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryOptimizationRequestPolicyTest {
    @Test
    fun requestsOnAndroidMAndNewerWhenNotExemptAndNeverRequested() {
        assertTrue(
            BatteryOptimizationRequestPolicy.shouldRequest(
                sdkInt = 23,
                isIgnoringBatteryOptimizations = false,
                hasRequestedBefore = false,
            ),
        )
    }

    @Test
    fun doesNotRequestBeforeAndroidM() {
        assertFalse(
            BatteryOptimizationRequestPolicy.shouldRequest(
                sdkInt = 22,
                isIgnoringBatteryOptimizations = false,
                hasRequestedBefore = false,
            ),
        )
    }

    @Test
    fun doesNotRequestWhenAlreadyExempt() {
        assertFalse(
            BatteryOptimizationRequestPolicy.shouldRequest(
                sdkInt = 23,
                isIgnoringBatteryOptimizations = true,
                hasRequestedBefore = false,
            ),
        )
    }

    @Test
    fun doesNotRequestMoreThanOnce() {
        assertFalse(
            BatteryOptimizationRequestPolicy.shouldRequest(
                sdkInt = 23,
                isIgnoringBatteryOptimizations = false,
                hasRequestedBefore = true,
            ),
        )
    }
}

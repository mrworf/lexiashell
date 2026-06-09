package nu.sensenet.lexiashell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashRevealPolicyTest {
    @Test
    fun pageFinishWithoutConsoleOutputRevealsAfterThreeSecondFallback() {
        val policy = SplashRevealPolicy()

        val initial = policy.onPageFinished(nowMs = 10_000L)

        assertFalse(initial.revealNow)
        assertEquals(3_000L, initial.scheduleDelayMs)
        assertTrue(policy.decision(nowMs = 13_000L).revealNow)
    }

    @Test
    fun consoleOutputAfterPageFinishDelaysRevealUntilOneSecondOfSilence() {
        val policy = SplashRevealPolicy()

        policy.onPageFinished(nowMs = 10_000L)
        val afterConsole = policy.onConsoleOutput(nowMs = 10_500L)

        assertFalse(afterConsole.revealNow)
        assertEquals(1_000L, afterConsole.scheduleDelayMs)
        assertFalse(policy.decision(nowMs = 11_499L).revealNow)
        assertTrue(policy.decision(nowMs = 11_500L).revealNow)
    }

    @Test
    fun consoleIdleBeforePageFinishStillWaitsForPageFinish() {
        val policy = SplashRevealPolicy()

        val beforePageFinish = policy.onConsoleOutput(nowMs = 10_000L)
        val afterPageFinish = policy.onPageFinished(nowMs = 12_000L)

        assertFalse(beforePageFinish.revealNow)
        assertNull(beforePageFinish.scheduleDelayMs)
        assertTrue(afterPageFinish.revealNow)
    }

    @Test
    fun repeatedConsoleOutputPushesRevealTimeForward() {
        val policy = SplashRevealPolicy()

        policy.onPageFinished(nowMs = 10_000L)
        policy.onConsoleOutput(nowMs = 10_500L)
        val afterSecondConsole = policy.onConsoleOutput(nowMs = 11_200L)

        assertFalse(afterSecondConsole.revealNow)
        assertEquals(1_000L, afterSecondConsole.scheduleDelayMs)
        assertFalse(policy.decision(nowMs = 12_199L).revealNow)
        assertTrue(policy.decision(nowMs = 12_200L).revealNow)
    }

    @Test
    fun resetClearsPendingRevealState() {
        val policy = SplashRevealPolicy()

        policy.onPageFinished(nowMs = 10_000L)
        policy.reset()

        val decision = policy.decision(nowMs = 13_000L)
        assertFalse(decision.revealNow)
        assertNull(decision.scheduleDelayMs)
    }
}

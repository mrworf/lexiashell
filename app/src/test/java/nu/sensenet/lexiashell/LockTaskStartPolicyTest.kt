package nu.sensenet.lexiashell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockTaskStartPolicyTest {
    @Test
    fun startsWhenAppIsPermittedAndKeyguardIsUnlocked() {
        assertTrue(
            LockTaskStartPolicy.shouldStart(
                isLockTaskPermitted = true,
                isKeyguardLocked = false,
            ),
        )
    }

    @Test
    fun doesNotStartWhenAppIsNotPermitted() {
        assertFalse(
            LockTaskStartPolicy.shouldStart(
                isLockTaskPermitted = false,
                isKeyguardLocked = false,
            ),
        )
    }

    @Test
    fun doesNotStartWhenKeyguardIsLocked() {
        assertFalse(
            LockTaskStartPolicy.shouldStart(
                isLockTaskPermitted = true,
                isKeyguardLocked = true,
            ),
        )
    }
}

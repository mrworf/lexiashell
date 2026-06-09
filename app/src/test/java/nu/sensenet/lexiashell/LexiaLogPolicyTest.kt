package nu.sensenet.lexiashell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LexiaLogPolicyTest {
    @Test
    fun allowsDebugLogsForDebugBuilds() {
        assertTrue(LexiaLogPolicy.shouldLogDebug(isDebugBuild = true))
    }

    @Test
    fun suppressesDebugLogsForReleaseBuilds() {
        assertFalse(LexiaLogPolicy.shouldLogDebug(isDebugBuild = false))
    }
}

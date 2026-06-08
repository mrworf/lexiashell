package nu.sensenet.lexiashell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.mozilla.geckoview.GeckoSession

class GeckoLoadPolicyTest {
    @Test
    fun bypassesCacheForInitialLexiaLoad() {
        assertEquals(GeckoSession.LOAD_FLAGS_BYPASS_CACHE, GeckoLoadPolicy.flags())
    }

    @Test
    fun doesNotUseDefaultLoadFlags() {
        assertNotEquals(GeckoSession.LOAD_FLAGS_NONE, GeckoLoadPolicy.flags())
    }
}

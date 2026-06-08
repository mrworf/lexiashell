package nu.sensenet.lexiashell

import android.webkit.WebSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WebViewCachePolicyTest {
    @Test
    fun selectsNoCacheMode() {
        assertEquals(WebSettings.LOAD_NO_CACHE, WebViewCachePolicy.cacheMode())
    }

    @Test
    fun doesNotUseDefaultCacheMode() {
        assertNotEquals(WebSettings.LOAD_DEFAULT, WebViewCachePolicy.cacheMode())
    }
}

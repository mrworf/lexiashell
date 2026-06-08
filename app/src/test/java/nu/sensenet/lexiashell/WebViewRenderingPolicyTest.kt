package nu.sensenet.lexiashell

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WebViewRenderingPolicyTest {
    @Test
    fun selectsDefaultLayerType() {
        assertEquals(View.LAYER_TYPE_NONE, WebViewRenderingPolicy.layerType())
    }

    @Test
    fun doesNotUseSoftwareLayerType() {
        assertNotEquals(View.LAYER_TYPE_SOFTWARE, WebViewRenderingPolicy.layerType())
    }
}

package nu.sensenet.lexiashell

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WebViewRenderingPolicyTest {
    @Test
    fun selectsSoftwareLayerType() {
        assertEquals(View.LAYER_TYPE_SOFTWARE, WebViewRenderingPolicy.layerType())
    }

    @Test
    fun doesNotUseHardwareLayerType() {
        assertNotEquals(View.LAYER_TYPE_HARDWARE, WebViewRenderingPolicy.layerType())
    }
}

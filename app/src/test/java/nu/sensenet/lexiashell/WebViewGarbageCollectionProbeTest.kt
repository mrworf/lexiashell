package nu.sensenet.lexiashell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewGarbageCollectionProbeTest {
    @Test
    fun scriptGuardsGarbageCollectionCall() {
        val script = WebViewGarbageCollectionProbe.script()

        assertTrue(script.contains("typeof globalThis.gc !== 'function'"))
        assertTrue(script.contains("globalThis.gc();"))
    }

    @Test
    fun scriptReturnsUnavailableWhenGarbageCollectionIsNotExposed() {
        assertTrue(WebViewGarbageCollectionProbe.script().contains("return 'unavailable';"))
    }

    @Test
    fun formatsRawEvaluateJavascriptResult() {
        assertEquals(
            "WebView JS GC probe: evaluateJavascriptResult=\"unavailable\"",
            WebViewGarbageCollectionProbe.resultLine("\"unavailable\""),
        )
    }

    @Test
    fun formatsNullEvaluateJavascriptResult() {
        assertEquals(
            "WebView JS GC probe: evaluateJavascriptResult=null",
            WebViewGarbageCollectionProbe.resultLine(null),
        )
    }
}

package nu.sensenet.lexiashell

import android.webkit.WebChromeClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomViewSessionTest {
    @Test
    fun finishNotifiesActiveCallbackOnce() {
        val session = CustomViewSession()
        val callback = CountingCallback()

        assertTrue(session.begin(callback))
        assertTrue(session.finish())
        assertFalse(session.finish())

        assertEquals(1, callback.hideCount)
    }

    @Test
    fun secondBeginRejectsAndHidesIncomingCallback() {
        val session = CustomViewSession()
        val activeCallback = CountingCallback()
        val rejectedCallback = CountingCallback()

        assertTrue(session.begin(activeCallback))
        assertFalse(session.begin(rejectedCallback))

        assertEquals(0, activeCallback.hideCount)
        assertEquals(1, rejectedCallback.hideCount)
    }

    private class CountingCallback : WebChromeClient.CustomViewCallback {
        var hideCount = 0

        override fun onCustomViewHidden() {
            hideCount += 1
        }
    }
}

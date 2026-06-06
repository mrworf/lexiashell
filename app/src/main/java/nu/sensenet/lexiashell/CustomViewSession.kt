package nu.sensenet.lexiashell

import android.webkit.WebChromeClient

class CustomViewSession {
    private var callback: WebChromeClient.CustomViewCallback? = null

    fun begin(callback: WebChromeClient.CustomViewCallback): Boolean {
        if (this.callback != null) {
            callback.onCustomViewHidden()
            return false
        }

        this.callback = callback
        return true
    }

    fun finish(): Boolean {
        val activeCallback = callback ?: return false
        callback = null
        activeCallback.onCustomViewHidden()
        return true
    }
}

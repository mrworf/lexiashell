package nu.sensenet.lexiashell

import android.util.Log

interface LexiaLogger {
    fun debug(message: String)
    fun error(message: String, throwable: Throwable? = null)
}

object AndroidLexiaLogger : LexiaLogger {
    override fun debug(message: String) {
        Log.d(TAG, message)
    }

    override fun error(message: String, throwable: Throwable?) {
        if (throwable == null) {
            Log.e(TAG, message)
        } else {
            Log.e(TAG, message, throwable)
        }
    }

    private const val TAG = "LexiaShell"
}

package nu.sensenet.lexiashell

import android.util.Log

interface LexiaLogger {
    fun debug(message: String)
    fun error(message: String, throwable: Throwable? = null)
}

object AndroidLexiaLogger : LexiaLogger {
    fun startup(message: String) {
        Log.i(TAG, message)
    }

    override fun debug(message: String) {
        if (LexiaLogPolicy.shouldLogDebug(BuildConfig.DEBUG)) {
            Log.d(TAG, message)
        }
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

object LexiaLogPolicy {
    fun shouldLogDebug(isDebugBuild: Boolean): Boolean = isDebugBuild
}

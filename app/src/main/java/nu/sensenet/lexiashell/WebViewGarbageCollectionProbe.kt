package nu.sensenet.lexiashell

object WebViewGarbageCollectionProbe {
    fun script(): String =
        """
        (function() {
          try {
            if (typeof globalThis !== 'object' || typeof globalThis.gc !== 'function') {
              return 'unavailable';
            }
            globalThis.gc();
            return 'invoked';
          } catch (error) {
            return 'errored:' + (error && error.name ? error.name : 'unknown');
          }
        })();
        """.trimIndent()

    fun resultLine(rawResult: String?): String =
        "WebView JS GC probe: evaluateJavascriptResult=${rawResult ?: "null"}"
}

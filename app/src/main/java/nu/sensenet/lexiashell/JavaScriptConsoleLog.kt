package nu.sensenet.lexiashell

object JavaScriptConsoleLog {
    fun format(level: String, message: String, sourceId: String?, lineNumber: Int): String {
        val source = sourceId
            ?.takeIf { it.isNotBlank() }
            ?: "(unknown source)"

        return "JavaScript console $level $source:$lineNumber $message"
    }
}

package nu.sensenet.lexiashell

import org.junit.Assert.assertEquals
import org.junit.Test

class JavaScriptConsoleLogTest {
    @Test
    fun formatsConsoleMessageWithSourceLocation() {
        val message = JavaScriptConsoleLog.format(
            level = "ERROR",
            message = "login state reset",
            sourceId = "https://www.lexiacore5.com/main.js",
            lineNumber = 42,
        )

        assertEquals(
            "JavaScript console ERROR https://www.lexiacore5.com/main.js:42 login state reset",
            message,
        )
    }

    @Test
    fun formatsConsoleMessageWithUnknownSourceWhenSourceIsBlank() {
        val message = JavaScriptConsoleLog.format(
            level = "LOG",
            message = "loaded",
            sourceId = "",
            lineNumber = 0,
        )

        assertEquals(
            "JavaScript console LOG (unknown source):0 loaded",
            message,
        )
    }
}

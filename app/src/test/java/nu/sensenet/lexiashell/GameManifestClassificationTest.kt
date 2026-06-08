package nu.sensenet.lexiashell

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class GameManifestClassificationTest {
    @Test
    fun manifestDeclaresGameCategory() {
        assertTrue(manifestText().contains("""android:appCategory="game""""))
    }

    @Test
    fun manifestDeclaresDeprecatedGameCompatibilityFlag() {
        assertTrue(manifestText().contains("""android:isGame="true""""))
    }

    private fun manifestText(): String =
        File("src/main/AndroidManifest.xml").readText()
}

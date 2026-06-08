package nu.sensenet.lexiashell

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeDiagnosticsTest {
    @Test
    fun formatsGameClassificationWithGameMode() {
        assertEquals(
            "Game classification: appCategory=game(0) legacyIsGame=true gameMode=2",
            RuntimeDiagnostics.gameClassificationLine(
                applicationCategory = 0,
                legacyIsGame = true,
                gameMode = 2,
            ),
        )
    }

    @Test
    fun formatsGameClassificationWithoutGameMode() {
        assertEquals(
            "Game classification: appCategory=undefined(-1) legacyIsGame=false gameMode=unavailable",
            RuntimeDiagnostics.gameClassificationLine(
                applicationCategory = -1,
                legacyIsGame = false,
                gameMode = null,
            ),
        )
    }

    @Test
    fun formatsBatteryOptimizationState() {
        assertEquals(
            "Battery optimization: ignoring=true",
            RuntimeDiagnostics.batteryOptimizationLine(isIgnoringBatteryOptimizations = true),
        )
    }

    @Test
    fun formatsUnsupportedBatteryOptimizationState() {
        assertEquals(
            "Battery optimization: ignoring=unsupported",
            RuntimeDiagnostics.batteryOptimizationLine(isIgnoringBatteryOptimizations = null),
        )
    }

    @Test
    fun formatsKnownMemoryPressureLevel() {
        assertEquals(
            "Memory pressure: level=15 meaning=running-critical",
            RuntimeDiagnostics.memoryPressureLine(level = 15),
        )
    }

    @Test
    fun formatsUnknownMemoryPressureLevel() {
        assertEquals(
            "Memory pressure: level=999 meaning=unknown",
            RuntimeDiagnostics.memoryPressureLine(level = 999),
        )
    }
}

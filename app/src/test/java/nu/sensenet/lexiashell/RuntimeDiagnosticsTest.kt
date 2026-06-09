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

    @Test
    fun formatsRuntimeUptimeWithProcessTime() {
        assertEquals(
            "Runtime uptime: processElapsedMs=1234 activityElapsedMs=56",
            RuntimeDiagnostics.runtimeUptimeLine(
                processElapsedRealtimeMs = 1234,
                activityElapsedRealtimeMs = 56,
            ),
        )
    }

    @Test
    fun formatsRuntimeUptimeWithoutProcessTime() {
        assertEquals(
            "Runtime uptime: processElapsedMs=unavailable activityElapsedMs=56",
            RuntimeDiagnostics.runtimeUptimeLine(
                processElapsedRealtimeMs = null,
                activityElapsedRealtimeMs = 56,
            ),
        )
    }

    @Test
    fun formatsGeckoViewVersion() {
        assertEquals(
            "GeckoView version: 152.0.1",
            RuntimeDiagnostics.geckoViewVersionLine("152.0.1"),
        )
    }

    @Test
    fun formatsGeckoViewHardwareAccelerationState() {
        assertEquals(
            "GeckoView hardware acceleration: enabled=true",
            RuntimeDiagnostics.geckoViewHardwareAccelerationLine(isHardwareAccelerated = true),
        )
    }

    @Test
    fun formatsGeckoContentProcessCrash() {
        assertEquals(
            "GeckoView content process crashed",
            RuntimeDiagnostics.geckoContentProcessCrashLine(),
        )
    }

    @Test
    fun formatsGeckoContentProcessKill() {
        assertEquals(
            "GeckoView content process killed",
            RuntimeDiagnostics.geckoContentProcessKillLine(),
        )
    }

    @Test
    fun formatsRecentExitOnOneLine() {
        assertEquals(
            "Recent exit: process=nu.sensenet.lexiashell reason=5 status=0 " +
                "importance=100 pssKb=2048 rssKb=4096 timestampMs=123456 " +
                "description=native crash",
            RuntimeDiagnostics.recentExitLine(
                processName = "nu.sensenet.lexiashell",
                reason = 5,
                status = 0,
                importance = 100,
                pssKb = 2048,
                rssKb = 4096,
                timestampMs = 123456,
                description = "native\ncrash",
            ),
        )
    }
}

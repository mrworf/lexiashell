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
    fun formatsWebViewProvider() {
        assertEquals(
            "WebView provider: package=com.google.android.webview " +
                "versionName=149.0.7827.48 versionCode=782704803",
            RuntimeDiagnostics.webViewProviderLine(
                packageName = "com.google.android.webview",
                versionName = "149.0.7827.48",
                versionCode = 782704803,
            ),
        )
    }

    @Test
    fun formatsUnavailableWebViewProvider() {
        assertEquals(
            "WebView provider: package=unavailable versionName=unavailable versionCode=unavailable",
            RuntimeDiagnostics.webViewProviderLine(
                packageName = null,
                versionName = null,
                versionCode = null,
            ),
        )
    }

    @Test
    fun formatsWebViewHardwareAccelerationState() {
        assertEquals(
            "WebView hardware acceleration: enabled=true",
            RuntimeDiagnostics.webViewHardwareAccelerationLine(isHardwareAccelerated = true),
        )
    }

    @Test
    fun formatsRenderProcessGone() {
        assertEquals(
            "WebView render process gone: didCrash=true rendererPriorityAtExit=0",
            RuntimeDiagnostics.renderProcessGoneLine(
                didCrash = true,
                rendererPriorityAtExit = 0,
            ),
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

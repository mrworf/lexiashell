package nu.sensenet.lexiashell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.geckoview.StorageController

class LoadingRecoveryPolicyTest {
    @Test
    fun successfulPageStopRevealsBrowserAfterConfiguredFade() {
        assertEquals(
            BrowserLoadAction.REVEAL_BROWSER,
            LoadingRecoveryPolicy.actionFor(BrowserLoadEvent.PAGE_STOP_SUCCESS),
        )
        assertEquals(250L, LoadingRecoveryPolicy.BROWSER_REVEAL_ANIMATION_MS)
    }

    @Test
    fun failuresDoNotRevealBrowser() {
        val failures = listOf(
            BrowserLoadEvent.PAGE_STOP_FAILURE,
            BrowserLoadEvent.LOAD_ERROR,
            BrowserLoadEvent.CONTENT_CRASH,
            BrowserLoadEvent.CONTENT_KILL,
        )

        for (failure in failures) {
            assertEquals(BrowserLoadAction.SHOW_FAILURE, LoadingRecoveryPolicy.actionFor(failure))
        }
    }

    @Test
    fun retryRequestsStorageClearingBeforeReload() {
        assertTrue(LoadingRecoveryPolicy.retryClearsStorage(FailureDialogAction.RETRY))
        assertEquals(StorageController.ClearFlags.ALL, LoadingRecoveryPolicy.storageClearFlags())
    }

    @Test
    fun quitMapsToAppExit() {
        assertTrue(LoadingRecoveryPolicy.quitsApp(FailureDialogAction.QUIT))
        assertFalse(LoadingRecoveryPolicy.quitsApp(FailureDialogAction.RETRY))
    }
}


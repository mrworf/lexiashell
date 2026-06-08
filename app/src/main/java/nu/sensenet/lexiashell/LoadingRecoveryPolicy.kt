package nu.sensenet.lexiashell

import org.mozilla.geckoview.StorageController

enum class BrowserLoadEvent {
    PAGE_STOP_SUCCESS,
    PAGE_STOP_FAILURE,
    LOAD_ERROR,
    CONTENT_CRASH,
    CONTENT_KILL,
}

enum class BrowserLoadAction {
    REVEAL_BROWSER,
    SHOW_FAILURE,
}

enum class FailureDialogAction {
    RETRY,
    QUIT,
}

object LoadingRecoveryPolicy {
    const val BROWSER_REVEAL_ANIMATION_MS = 250L

    fun actionFor(event: BrowserLoadEvent): BrowserLoadAction =
        when (event) {
            BrowserLoadEvent.PAGE_STOP_SUCCESS -> BrowserLoadAction.REVEAL_BROWSER
            BrowserLoadEvent.PAGE_STOP_FAILURE,
            BrowserLoadEvent.LOAD_ERROR,
            BrowserLoadEvent.CONTENT_CRASH,
            BrowserLoadEvent.CONTENT_KILL,
            -> BrowserLoadAction.SHOW_FAILURE
        }

    fun retryClearsStorage(action: FailureDialogAction): Boolean =
        action == FailureDialogAction.RETRY

    fun quitsApp(action: FailureDialogAction): Boolean =
        action == FailureDialogAction.QUIT

    fun storageClearFlags(): Long = StorageController.ClearFlags.ALL
}


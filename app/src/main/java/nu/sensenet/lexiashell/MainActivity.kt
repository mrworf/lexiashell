package nu.sensenet.lexiashell

import android.animation.ObjectAnimator
import android.annotation.TargetApi
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.GameManager
import android.app.GameState
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebRequestError

class MainActivity : Activity() {
    private lateinit var rootLayout: FrameLayout
    private lateinit var geckoView: GeckoView
    private lateinit var loadingOverlay: View
    private lateinit var geckoSession: GeckoSession
    private val logger = AndroidLexiaLogger
    private var navigationPolicy = CspNavigationPolicy.fromCsp(null, LEXIA_CORE5_URL)
    private var loadingFailureDialog: AlertDialog? = null
    private var isGeckoSessionClosed = true
    private var isLoadingOverlayActive = true
    private val splashRevealHandler = Handler(Looper.getMainLooper())
    private val splashRevealRunnable = Runnable {
        revealBrowser()
    }
    @Volatile
    private var isDestroyed = false
    private val activityCreatedElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger.startup(BuildProvenance.startupLogLine())
        logRuntimeDiagnostics()
        logger.debug("MainActivity onCreate")

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        configureFullscreenWindow()

        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
        }
        geckoView = GeckoView(this).apply {
            id = R.id.gecko_view
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )

            alpha = 0f
            isLongClickable = false
            setOnLongClickListener { true }
            setBackgroundColor(Color.BLACK)
        }
        loadingOverlay = createLoadingOverlay()
        geckoSession = createGeckoSession()

        rootLayout.addView(geckoView)
        rootLayout.addView(loadingOverlay)
        setContentView(rootLayout)
        geckoView.setSession(geckoSession)
        logGeckoViewRuntimeDiagnostics()
        hideSystemBars()
        logger.debug("Initial GeckoView content set; starting policy fetch")
        loadLexiaAfterPolicyFetch()
    }

    override fun onResume() {
        super.onResume()
        logger.debug("MainActivity onResume")
        hideSystemBars()
        startLockTaskWhenPermitted()
        reportGameState(isPageLoading = false)
    }

    override fun onPause() {
        logger.debug("MainActivity onPause; leaving GeckoView runtime active")
        super.onPause()
    }

    override fun onDestroy() {
        logger.debug("MainActivity onDestroy")
        isDestroyed = true
        resetSplashRevealState()
        if (this::geckoSession.isInitialized) {
            closeGeckoSession()
        }
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        logger.debug(RuntimeDiagnostics.memoryPressureLine(level))
    }

    override fun onLowMemory() {
        super.onLowMemory()
        logger.debug("Memory pressure: onLowMemory")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        logger.debug("Configuration changed: orientation=${newConfig.orientation}")
        if (this::geckoSession.isInitialized) {
            processGeckoRuntime(this).configurationChanged(newConfig)
        }
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        logger.debug("Window focus changed: hasFocus=$hasFocus")
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun createGeckoSession(): GeckoSession {
        val settings = GeckoSessionSettings()
        GeckoSessionSettingsPolicy.applyTo(settings)

        return GeckoSession(settings).apply {
            setNavigationDelegate(createNavigationDelegate())
            setProgressDelegate(createProgressDelegate())
            setContentDelegate(createContentDelegate())
            setPermissionDelegate(createPermissionDelegate())
            open(processGeckoRuntime(this@MainActivity))
            isGeckoSessionClosed = false
        }
    }

    private fun createNavigationDelegate(): GeckoSession.NavigationDelegate =
        object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny>? {
                if (request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW) {
                    logger.debug("Blocked new-window navigation ${request.uri}")
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                val decision = navigationPolicy.evaluate(request.uri)
                logger.debug(
                    "Main-frame navigation ${if (decision.allowed) "allowed" else "blocked"} " +
                        "${request.uri}: ${decision.reason}",
                )
                return GeckoResult.fromValue(
                    if (decision.allowed) AllowOrDeny.ALLOW else AllowOrDeny.DENY,
                )
            }

            override fun onSubframeLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny>? {
                logger.debug("Ignoring subframe navigation ${request.uri}")
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }

            override fun onNewSession(
                session: GeckoSession,
                uri: String,
            ): GeckoResult<GeckoSession>? {
                logger.debug("Blocked new GeckoView session for $uri")
                return null
            }

            override fun onLoadError(
                session: GeckoSession,
                uri: String?,
                error: WebRequestError,
            ): GeckoResult<String>? {
                logger.error(
                    "GeckoView load error for ${uri ?: "(unknown URL)"}: " +
                        "category=${error.category} code=${error.code} " +
                        "message=${error.message ?: "unavailable"}",
                        error,
                )
                handleBrowserLoadEvent(
                    BrowserLoadEvent.LOAD_ERROR,
                    "Lexia Shell could not load ${uri ?: LEXIA_CORE5_URL}. " +
                        "GeckoView reported category=${error.category}, code=${error.code}, " +
                        "message=${error.message ?: "unavailable"}.",
                )
                return null
            }
        }

    private fun createProgressDelegate(): GeckoSession.ProgressDelegate =
        object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                logger.debug("Page started $url")
                resetSplashRevealState()
                if (isLoadingOverlayActive) {
                    showLoadingOverlay()
                }
                reportGameState(isPageLoading = true)
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                logger.debug("Page finished success=$success")
                reportGameState(isPageLoading = false)
                handleBrowserLoadEvent(
                    if (success) {
                        BrowserLoadEvent.PAGE_STOP_SUCCESS
                    } else {
                        BrowserLoadEvent.PAGE_STOP_FAILURE
                    },
                    "Lexia Shell started loading $LEXIA_CORE5_URL, but GeckoView reported that " +
                        "the page did not finish successfully.",
                )
            }
        }

    private fun createContentDelegate(): GeckoSession.ContentDelegate =
        object : GeckoSession.ContentDelegate {
            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                logger.debug(
                    "GeckoView fullscreen ${if (fullScreen) "entered" else "exited"}",
                )
                hideSystemBars()
            }

            override fun onFocusRequest(session: GeckoSession) {
                geckoView.requestFocus()
            }

            override fun onCrash(session: GeckoSession) {
                logger.error(RuntimeDiagnostics.geckoContentProcessCrashLine())
                reportGameState(isPageLoading = false)
                handleBrowserLoadEvent(
                    BrowserLoadEvent.CONTENT_CRASH,
                    "The embedded GeckoView browser process crashed while loading Lexia.",
                )
            }

            override fun onKill(session: GeckoSession) {
                logger.error(RuntimeDiagnostics.geckoContentProcessKillLine())
                reportGameState(isPageLoading = false)
                handleBrowserLoadEvent(
                    BrowserLoadEvent.CONTENT_KILL,
                    "Android stopped the embedded GeckoView browser process while loading Lexia.",
                )
            }
        }

    private fun createPermissionDelegate(): GeckoSession.PermissionDelegate =
        object : GeckoSession.PermissionDelegate {
            override fun onContentPermissionRequest(
                session: GeckoSession,
                perm: GeckoSession.PermissionDelegate.ContentPermission,
            ): GeckoResult<Int>? {
                val value = GeckoContentPermissionPolicy.valueFor(perm.permission)
                logger.debug(
                    "GeckoView content permission " +
                        "${GeckoContentPermissionPolicy.decisionName(value)} " +
                        "permission=${perm.permission} uri=${perm.uri}",
                )
                return GeckoResult.fromValue(value)
            }

            override fun onAndroidPermissionsRequest(
                session: GeckoSession,
                permissions: Array<String>?,
                callback: GeckoSession.PermissionDelegate.Callback,
            ) {
                logger.debug(
                    "Rejected GeckoView Android permission request " +
                        permissions.orEmpty().joinToString(prefix = "[", postfix = "]"),
                )
                callback.reject()
            }

            override fun onMediaPermissionRequest(
                session: GeckoSession,
                uri: String,
                video: Array<GeckoSession.PermissionDelegate.MediaSource>?,
                audio: Array<GeckoSession.PermissionDelegate.MediaSource>?,
                callback: GeckoSession.PermissionDelegate.MediaCallback,
            ) {
                logger.debug(
                    "Rejected GeckoView media permission request for $uri " +
                        "video=${video.orEmpty().size} audio=${audio.orEmpty().size}",
                )
                callback.reject()
            }
        }

    private fun createLoadingOverlay(): View {
        val content = LinearLayout(this).apply {
            id = R.id.loading_overlay
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        content.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.lexia_shell_logo)
                adjustViewBounds = true
                contentDescription = getString(R.string.app_name)
                layoutParams = LinearLayout.LayoutParams(dp(360), dp(360)).apply {
                    bottomMargin = dp(28)
                }
            },
        )
        content.addView(
            createIndeterminateLoadingBar(),
        )

        return content
    }

    private fun createIndeterminateLoadingBar(): View {
        val width = resources.displayMetrics.widthPixels / 4
        val height = dp(6)
        val segmentWidth = width / 3
        val radius = height / 2f

        val track = FrameLayout(this).apply {
            clipToOutline = true
            background = roundedDrawable(color = 0xFFE3E8EF.toInt(), radius = radius)
            layoutParams = LinearLayout.LayoutParams(width, height)
        }
        val segment = View(this).apply {
            background = roundedDrawable(color = 0xFF2563EB.toInt(), radius = radius)
            layoutParams = FrameLayout.LayoutParams(segmentWidth, height)
        }
        track.addView(segment)

        ObjectAnimator.ofFloat(segment, View.TRANSLATION_X, -segmentWidth.toFloat(), width.toFloat())
            .apply {
                duration = 1_000L
                repeatCount = ObjectAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }

        return track
    }

    private fun roundedDrawable(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun showLoadingOverlay() {
        if (!this::geckoView.isInitialized || !this::loadingOverlay.isInitialized) {
            return
        }

        geckoView.animate().cancel()
        geckoView.alpha = 0f
        loadingOverlay.animate().cancel()
        loadingOverlay.alpha = 1f
        loadingOverlay.visibility = View.VISIBLE
    }

    private fun handleBrowserLoadEvent(event: BrowserLoadEvent, detail: String) {
        when (LoadingRecoveryPolicy.actionFor(event)) {
            BrowserLoadAction.REVEAL_BROWSER -> handleSuccessfulPageStop()
            BrowserLoadAction.SHOW_FAILURE -> {
                resetSplashRevealState()
                showLoadingFailure(detail)
            }
        }
    }

    private fun handleSuccessfulPageStop() {
        splashRevealHandler.removeCallbacks(splashRevealRunnable)
        splashRevealHandler.postDelayed(splashRevealRunnable, SPLASH_REVEAL_DELAY_MS)
    }

    private fun resetSplashRevealState() {
        splashRevealHandler.removeCallbacks(splashRevealRunnable)
    }

    private fun revealBrowser() {
        if (isDestroyed) {
            return
        }

        splashRevealHandler.removeCallbacks(splashRevealRunnable)
        loadingFailureDialog?.dismiss()
        loadingFailureDialog = null
        isLoadingOverlayActive = false
        geckoView.animate().cancel()
        loadingOverlay.animate().cancel()
        geckoView.visibility = View.VISIBLE
        geckoView.alpha = 0f
        loadingOverlay.alpha = 1f
        geckoView.animate()
            .alpha(1f)
            .setDuration(LoadingRecoveryPolicy.BROWSER_REVEAL_ANIMATION_MS)
            .start()
        loadingOverlay.animate()
            .alpha(0f)
            .setDuration(LoadingRecoveryPolicy.BROWSER_REVEAL_ANIMATION_MS)
            .withEndAction {
                if (!isDestroyed) {
                    loadingOverlay.visibility = View.GONE
                    loadingOverlay.alpha = 1f
                }
            }
            .start()
    }

    private fun showLoadingFailure(detail: String) {
        if (isDestroyed || loadingFailureDialog?.isShowing == true) {
            return
        }

        isLoadingOverlayActive = true
        showLoadingOverlay()
        loadingFailureDialog = AlertDialog.Builder(this)
            .setTitle(R.string.loading_failure_dialog_title)
            .setMessage(detail)
            .setPositiveButton(R.string.loading_failure_dialog_retry) { _, _ ->
                handleFailureDialogAction(FailureDialogAction.RETRY)
            }
            .setNegativeButton(R.string.loading_failure_dialog_quit) { _, _ ->
                handleFailureDialogAction(FailureDialogAction.QUIT)
            }
            .setCancelable(false)
            .show()
    }

    private fun handleFailureDialogAction(action: FailureDialogAction) {
        loadingFailureDialog = null
        if (LoadingRecoveryPolicy.retryClearsStorage(action)) {
            retryWithClearedBrowserData()
            return
        }

        if (LoadingRecoveryPolicy.quitsApp(action)) {
            quitApp()
        }
    }

    private fun retryWithClearedBrowserData() {
        logger.debug("Retry requested; clearing GeckoView storage before reload")
        isLoadingOverlayActive = true
        resetSplashRevealState()
        showLoadingOverlay()
        resetGeckoSession()

        processGeckoRuntime(this)
            .storageController
            .clearData(LoadingRecoveryPolicy.storageClearFlags())
            .accept(
                {
                    logger.debug("GeckoView storage cleared; retrying Lexia load")
                    loadLexiaAfterPolicyFetch()
                },
                { throwable ->
                    logger.error("GeckoView storage clearing failed; retrying anyway", throwable)
                    loadLexiaAfterPolicyFetch()
                },
            )
    }

    private fun resetGeckoSession() {
        resetSplashRevealState()
        closeGeckoSession()
        geckoSession = createGeckoSession()
        geckoView.setSession(geckoSession)
        logGeckoViewRuntimeDiagnostics()
    }

    private fun closeGeckoSession() {
        if (isGeckoSessionClosed) {
            return
        }

        geckoSession.stop()
        geckoSession.setNavigationDelegate(null)
        geckoSession.setProgressDelegate(null)
        geckoSession.setContentDelegate(null)
        geckoSession.setPermissionDelegate(null)
        if (this::geckoView.isInitialized) {
            geckoView.releaseSession()
        }
        geckoSession.close()
        isGeckoSessionClosed = true
    }

    private fun quitApp() {
        logger.debug("Quit requested from loading failure dialog")
        closeGeckoSession()
        finishAndRemoveTask()
    }

    private fun configureFullscreenWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun startLockTaskWhenPermitted() {
        val devicePolicyManager =
            getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val isLockTaskPermitted = devicePolicyManager.isLockTaskPermitted(packageName)
        val isKeyguardLocked = keyguardManager.isKeyguardLocked

        if (!LockTaskStartPolicy.shouldStart(isLockTaskPermitted, isKeyguardLocked)) {
            logger.debug(
                "Skipping lock task start: " +
                    "permitted=$isLockTaskPermitted keyguardLocked=$isKeyguardLocked",
            )
            return
        }

        logger.debug("Starting lock task mode")
        startLockTask()
    }

    @Suppress("DEPRECATION")
    private fun logRuntimeDiagnostics() {
        val applicationCategory =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationInfo.category
            } else {
                ApplicationInfo.CATEGORY_UNDEFINED
            }
        val legacyIsGame = applicationInfo.flags and ApplicationInfo.FLAG_IS_GAME != 0
        logger.debug(
            RuntimeDiagnostics.gameClassificationLine(
                applicationCategory = applicationCategory,
                legacyIsGame = legacyIsGame,
                gameMode = currentGameMode(),
            ),
        )
        logger.debug(
            RuntimeDiagnostics.runtimeUptimeLine(
                processElapsedRealtimeMs = processElapsedRealtimeMs(),
                activityElapsedRealtimeMs = activityElapsedRealtimeMs(),
            ),
        )
        logRecentExitReasons()
    }

    private fun logGeckoViewRuntimeDiagnostics() {
        logger.debug(RuntimeDiagnostics.geckoViewVersionLine(BuildConfig.GECKOVIEW_VERSION))
        logger.debug(
            RuntimeDiagnostics.geckoViewHardwareAccelerationLine(
                isHardwareAccelerated = geckoView.isHardwareAccelerated,
            ),
        )
    }

    private fun processElapsedRealtimeMs(): Long? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return null
        }

        return android.os.SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
    }

    private fun activityElapsedRealtimeMs(): Long =
        android.os.SystemClock.elapsedRealtime() - activityCreatedElapsedRealtimeMs

    private fun logRecentExitReasons() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            logger.debug("Recent exits: unavailable before Android 11")
            return
        }

        logRecentExitReasonsOnSupportedSdk()
    }

    @TargetApi(Build.VERSION_CODES.R)
    private fun logRecentExitReasonsOnSupportedSdk() {
        val activityManager = getSystemService(ActivityManager::class.java)
        val exitReasons = activityManager.getHistoricalProcessExitReasons(
            packageName,
            0,
            RECENT_EXIT_REASON_LIMIT,
        )

        if (exitReasons.isEmpty()) {
            logger.debug("Recent exits: none")
            return
        }

        for (exitReason in exitReasons) {
            logger.debug(
                RuntimeDiagnostics.recentExitLine(
                    processName = exitReason.processName,
                    reason = exitReason.reason,
                    status = exitReason.status,
                    importance = exitReason.importance,
                    pssKb = exitReason.pss,
                    rssKb = exitReason.rss,
                    timestampMs = exitReason.timestamp,
                    description = exitReason.description,
                ),
            )
        }
    }

    private fun currentGameMode(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return null
        }

        val gameManager = getSystemService(GameManager::class.java) ?: return null
        return gameManager.gameMode
    }

    private fun reportGameState(isPageLoading: Boolean) {
        if (!GameStateReportPolicy.shouldReport(Build.VERSION.SDK_INT)) {
            return
        }

        reportGameStateOnSupportedSdk(GameStateReportPolicy.report(isPageLoading))
    }

    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    private fun reportGameStateOnSupportedSdk(report: GameStateReport) {
        val gameManager = getSystemService(GameManager::class.java)
        if (gameManager == null) {
            logger.debug("Skipping game state report because GameManager is unavailable")
            return
        }

        gameManager.setGameState(
            GameState(
                report.isLoading,
                GameState.MODE_GAMEPLAY_UNINTERRUPTIBLE,
            ),
        )
        logger.debug("Reported game state: isLoading=${report.isLoading}")
    }

    private fun loadLexiaAfterPolicyFetch() {
        Thread {
            logger.debug("Policy fetch thread started for $LEXIA_CORE5_URL")
            val fetchResult = CspPolicyFetcher(logger = logger).fetch(LEXIA_CORE5_URL)
            if (isDestroyed) {
                logger.debug("Skipping Lexia load because activity was destroyed")
                return@Thread
            }

            runOnUiThread {
                if (!isDestroyed) {
                    if (fetchResult.hasTransportFailure) {
                        logger.error("CSP fetch failed; showing loading failure dialog")
                        showLoadingFailure(
                            "Lexia Shell could not fetch the security policy for " +
                                "$LEXIA_CORE5_URL. This usually means the network is unavailable, " +
                                "DNS failed, or Lexia did not respond in time.",
                        )
                        reportGameState(isPageLoading = false)
                        return@runOnUiThread
                    }

                    navigationPolicy = fetchResult.policy
                    logger.debug("Policy installed; loading $LEXIA_CORE5_URL")
                    geckoSession.load(
                        GeckoSession.Loader()
                            .uri(LEXIA_CORE5_URL)
                            .flags(GeckoLoadPolicy.flags()),
                    )
                } else {
                    logger.debug("Skipping Lexia load on UI thread because activity was destroyed")
                }
            }
        }.start()
    }

    companion object {
        private const val LEXIA_CORE5_URL = "https://www.lexiacore5.com"
        private const val SPLASH_REVEAL_DELAY_MS = 1_000L
        private const val RECENT_EXIT_REASON_LIMIT = 3
        private val GECKO_RUNTIME_LOCK = Any()
        @Volatile
        private var geckoRuntime: GeckoRuntime? = null

        private fun processGeckoRuntime(context: Context): GeckoRuntime {
            geckoRuntime?.let { return it }

            return synchronized(GECKO_RUNTIME_LOCK) {
                geckoRuntime ?: GeckoRuntime.create(
                    context.applicationContext,
                    GeckoRuntimeSettings.Builder().build(),
                ).also { geckoRuntime = it }
            }
        }
    }
}

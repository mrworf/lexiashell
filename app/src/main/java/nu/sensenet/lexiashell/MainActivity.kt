package nu.sensenet.lexiashell

import android.annotation.TargetApi
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.GameManager
import android.app.GameState
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
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
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebRequestError
import org.json.JSONObject

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
    private val splashRevealPolicy = SplashRevealPolicy()
    private val splashRevealHandler = Handler(Looper.getMainLooper())
    private val splashRevealRunnable = Runnable {
        applySplashRevealDecision(splashRevealPolicy.decision(SystemClock.elapsedRealtime()))
    }
    private var consoleIdleMonitorExtension: WebExtension? = null
    private val consoleIdleMonitorMessageDelegate = object : WebExtension.MessageDelegate {
        override fun onMessage(
            nativeApp: String,
            message: Any,
            sender: WebExtension.MessageSender,
        ): GeckoResult<Any>? {
            if (nativeApp != CONSOLE_IDLE_NATIVE_APP || sender.session !== geckoSession) {
                return null
            }

            if (message is JSONObject && message.optString("type") == CONSOLE_OUTPUT_MESSAGE_TYPE) {
                runOnUiThread { handleConsoleOutput() }
            }

            return null
        }
    }
    @Volatile
    private var isDestroyed = false
    private val diagnosticsHandler = Handler(Looper.getMainLooper())
    private val activityCreatedElapsedRealtimeMs = SystemClock.elapsedRealtime()
    private var isFdDiagnosticsRunning = false
    private val fdDiagnosticsRunnable = object : Runnable {
        override fun run() {
            if (!isFdDiagnosticsRunning || isDestroyed) {
                return
            }

            logFdSnapshot()
            diagnosticsHandler.postDelayed(this, FD_DIAGNOSTICS_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger.debug(BuildProvenance.startupLogLine())
        logRuntimeDiagnostics()
        logger.debug("MainActivity onCreate")

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        configureFullscreenWindow()

        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
        }
        geckoView = GeckoView(this).apply {
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
        installConsoleIdleMonitorThenLoadLexia()
    }

    override fun onResume() {
        super.onResume()
        logger.debug("MainActivity onResume")
        hideSystemBars()
        requestBatteryOptimizationExemptionWhenNeeded()
        startLockTaskWhenPermitted()
        reportGameState(isPageLoading = false)
        startFdDiagnostics()
    }

    override fun onPause() {
        logger.debug("MainActivity onPause; leaving GeckoView runtime active")
        stopFdDiagnostics()
        super.onPause()
    }

    override fun onDestroy() {
        logger.debug("MainActivity onDestroy")
        isDestroyed = true
        resetSplashRevealState()
        stopFdDiagnostics()
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
            ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                isIndeterminate = true
                layoutParams = LinearLayout.LayoutParams(
                    resources.displayMetrics.widthPixels / 4,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            },
        )

        return content
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
        applySplashRevealDecision(
            splashRevealPolicy.onPageFinished(SystemClock.elapsedRealtime()),
        )
    }

    private fun handleConsoleOutput() {
        if (isDestroyed || !isLoadingOverlayActive) {
            return
        }

        applySplashRevealDecision(
            splashRevealPolicy.onConsoleOutput(SystemClock.elapsedRealtime()),
        )
    }

    private fun applySplashRevealDecision(decision: SplashRevealDecision) {
        splashRevealHandler.removeCallbacks(splashRevealRunnable)
        if (decision.revealNow) {
            revealBrowser()
            return
        }

        val delayMs = decision.scheduleDelayMs ?: return
        splashRevealHandler.postDelayed(splashRevealRunnable, delayMs)
    }

    private fun resetSplashRevealState() {
        splashRevealHandler.removeCallbacks(splashRevealRunnable)
        splashRevealPolicy.reset()
    }

    private fun revealBrowser() {
        if (isDestroyed) {
            return
        }

        splashRevealHandler.removeCallbacks(splashRevealRunnable)
        loadingFailureDialog?.dismiss()
        loadingFailureDialog = null
        isLoadingOverlayActive = false
        geckoView.visibility = View.VISIBLE
        geckoView.animate()
            .alpha(1f)
            .setDuration(LoadingRecoveryPolicy.BROWSER_REVEAL_ANIMATION_MS)
            .withEndAction {
                if (!isDestroyed) {
                    loadingOverlay.visibility = View.GONE
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
                    installConsoleIdleMonitorThenLoadLexia()
                },
                { throwable ->
                    logger.error("GeckoView storage clearing failed; retrying anyway", throwable)
                    installConsoleIdleMonitorThenLoadLexia()
                },
            )
    }

    private fun resetGeckoSession() {
        resetSplashRevealState()
        closeGeckoSession()
        geckoSession = createGeckoSession()
        geckoView.setSession(geckoSession)
        consoleIdleMonitorExtension?.let { attachConsoleIdleMonitor(it, geckoSession) }
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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
            RuntimeDiagnostics.batteryOptimizationLine(
                isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations(),
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

        return SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
    }

    private fun activityElapsedRealtimeMs(): Long =
        SystemClock.elapsedRealtime() - activityCreatedElapsedRealtimeMs

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

    private fun isIgnoringBatteryOptimizations(): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return null
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestBatteryOptimizationExemptionWhenNeeded() {
        val isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations() ?: return
        val preferences = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val hasRequestedBefore = preferences.getBoolean(
            BATTERY_OPTIMIZATION_REQUESTED_KEY,
            false,
        )

        if (!BatteryOptimizationRequestPolicy.shouldRequest(
                sdkInt = Build.VERSION.SDK_INT,
                isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                hasRequestedBefore = hasRequestedBefore,
            )
        ) {
            logger.debug(
                "Skipping battery optimization exemption request: " +
                    "ignoring=$isIgnoringBatteryOptimizations requestedBefore=$hasRequestedBefore",
            )
            return
        }

        logger.debug("Showing battery optimization exemption explanation")
        AlertDialog.Builder(this)
            .setTitle(R.string.battery_exemption_dialog_title)
            .setMessage(R.string.battery_exemption_dialog_message)
            .setPositiveButton(R.string.battery_exemption_dialog_continue) { _, _ ->
                markBatteryOptimizationExemptionRequested(preferences)
                launchBatteryOptimizationExemptionRequest()
            }
            .setNegativeButton(R.string.battery_exemption_dialog_not_now) { _, _ ->
                markBatteryOptimizationExemptionRequested(preferences)
                logger.debug("Battery optimization exemption request postponed by user")
            }
            .setOnCancelListener {
                markBatteryOptimizationExemptionRequested(preferences)
                logger.debug("Battery optimization exemption explanation dismissed")
            }
            .show()
    }

    private fun markBatteryOptimizationExemptionRequested(
        preferences: android.content.SharedPreferences,
    ) {
        preferences.edit()
            .putBoolean(BATTERY_OPTIMIZATION_REQUESTED_KEY, true)
            .apply()
    }

    private fun launchBatteryOptimizationExemptionRequest() {
        logger.debug("Requesting battery optimization exemption")
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                },
            )
        } catch (exception: RuntimeException) {
            logger.error("Battery optimization exemption request failed", exception)
        }
    }

    private fun startFdDiagnostics() {
        if (isFdDiagnosticsRunning) {
            return
        }

        isFdDiagnosticsRunning = true
        logFdSnapshot()
        diagnosticsHandler.postDelayed(fdDiagnosticsRunnable, FD_DIAGNOSTICS_INTERVAL_MS)
    }

    private fun stopFdDiagnostics() {
        if (!isFdDiagnosticsRunning) {
            return
        }

        isFdDiagnosticsRunning = false
        diagnosticsHandler.removeCallbacks(fdDiagnosticsRunnable)
    }

    private fun logFdSnapshot() {
        logger.debug(
            FileDescriptorDiagnostics.snapshotLine(
                FileDescriptorDiagnostics.captureProcSelf(),
            ),
        )
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

    private fun installConsoleIdleMonitorThenLoadLexia() {
        consoleIdleMonitorExtension?.let { extension ->
            attachConsoleIdleMonitor(extension, geckoSession)
            loadLexiaAfterPolicyFetch()
            return
        }

        processGeckoRuntime(this)
            .webExtensionController
            .ensureBuiltIn(CONSOLE_IDLE_MONITOR_LOCATION, CONSOLE_IDLE_MONITOR_ID)
            .accept(
                { extension ->
                    runOnUiThread {
                        if (isDestroyed) {
                            return@runOnUiThread
                        }

                        if (extension == null) {
                            logger.error(
                                "Console idle monitor WebExtension was unavailable; " +
                                    "using no-console fallback",
                            )
                            loadLexiaAfterPolicyFetch()
                            return@runOnUiThread
                        }

                        logger.debug("Console idle monitor WebExtension installed")
                        consoleIdleMonitorExtension = extension
                        attachConsoleIdleMonitor(extension, geckoSession)
                        loadLexiaAfterPolicyFetch()
                    }
                },
                { throwable ->
                    runOnUiThread {
                        if (isDestroyed) {
                            return@runOnUiThread
                        }

                        logger.error(
                            "Console idle monitor WebExtension failed; using no-console fallback",
                            throwable,
                        )
                        loadLexiaAfterPolicyFetch()
                    }
                },
            )
    }

    private fun attachConsoleIdleMonitor(extension: WebExtension, session: GeckoSession) {
        session.webExtensionController.setMessageDelegate(
            extension,
            consoleIdleMonitorMessageDelegate,
            CONSOLE_IDLE_NATIVE_APP,
        )
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
        private const val CONSOLE_IDLE_MONITOR_LOCATION =
            "resource://android/assets/console_idle_monitor/"
        private const val CONSOLE_IDLE_MONITOR_ID = "console-idle-monitor@lexiashell.sensenet.nu"
        private const val CONSOLE_IDLE_NATIVE_APP = "browser"
        private const val CONSOLE_OUTPUT_MESSAGE_TYPE = "console-output"
        private const val PREFERENCES_NAME = "lexia_shell"
        private const val BATTERY_OPTIMIZATION_REQUESTED_KEY =
            "battery_optimization_exemption_requested"
        private const val FD_DIAGNOSTICS_INTERVAL_MS = 30_000L
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

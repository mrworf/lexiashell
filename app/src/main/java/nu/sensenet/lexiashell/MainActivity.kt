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
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebRequestError

class MainActivity : Activity() {
    private lateinit var geckoView: GeckoView
    private lateinit var geckoSession: GeckoSession
    private val logger = AndroidLexiaLogger
    private var navigationPolicy = CspNavigationPolicy.fromCsp(null, LEXIA_CORE5_URL)
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

        geckoView = GeckoView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )

            isLongClickable = false
            setOnLongClickListener { true }
            setBackgroundColor(Color.BLACK)
        }
        geckoSession = createGeckoSession()

        setContentView(geckoView)
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
        stopFdDiagnostics()
        if (this::geckoSession.isInitialized) {
            geckoSession.stop()
            geckoSession.setNavigationDelegate(null)
            geckoSession.setProgressDelegate(null)
            geckoSession.setContentDelegate(null)
            geckoSession.setPermissionDelegate(null)
            if (this::geckoView.isInitialized) {
                geckoView.releaseSession()
            }
            geckoSession.close()
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
                return null
            }
        }

    private fun createProgressDelegate(): GeckoSession.ProgressDelegate =
        object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                logger.debug("Page started $url")
                reportGameState(isPageLoading = true)
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                logger.debug("Page finished success=$success")
                reportGameState(isPageLoading = false)
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
            }

            override fun onKill(session: GeckoSession) {
                logger.error(RuntimeDiagnostics.geckoContentProcessKillLine())
                reportGameState(isPageLoading = false)
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

    private fun configureFullscreenWindow() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
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

    private fun loadLexiaAfterPolicyFetch() {
        Thread {
            logger.debug("Policy fetch thread started for $LEXIA_CORE5_URL")
            val fetchedPolicy = CspPolicyFetcher(logger = logger).fetch(LEXIA_CORE5_URL)
            if (isDestroyed) {
                logger.debug("Skipping Lexia load because activity was destroyed")
                return@Thread
            }

            runOnUiThread {
                if (!isDestroyed) {
                    navigationPolicy = fetchedPolicy
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

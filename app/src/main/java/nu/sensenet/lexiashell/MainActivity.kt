package nu.sensenet.lexiashell

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private val logger = AndroidLexiaLogger
    private var navigationPolicy = CspNavigationPolicy.fromCsp(null, LEXIA_CORE5_URL)
    @Volatile
    private var isDestroyed = false
    private var customView: View? = null
    private val customViewSession = CustomViewSession()
    private var originalSystemUiVisibility = 0

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger.debug("MainActivity onCreate")

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        configureFullscreenWindow()

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )

            isLongClickable = false
            setOnLongClickListener { true }
            setBackgroundColor(Color.BLACK)

            configureSettings(settings)
            configureCookies(this)
            logger.debug("Configured WebView settings and cookies")

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    if (!request.isForMainFrame) {
                        logger.debug("Ignoring subframe navigation ${request.url}")
                        return false
                    }

                    val decision = navigationPolicy.evaluate(request.url.toString())
                    logger.debug(
                        "Main-frame navigation ${if (decision.allowed) "allowed" else "blocked"} " +
                            "${request.url}: ${decision.reason}",
                    )
                    return !decision.allowed
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    logger.debug("Page started $url")
                }

                override fun onPageFinished(view: WebView, url: String) {
                    logger.debug("Page finished $url")
                }

                @TargetApi(Build.VERSION_CODES.M)
                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    val frame = if (request.isForMainFrame) "main-frame" else "subframe"
                    logger.error(
                        "WebView load error for $frame ${request.url}: " +
                            "${error.errorCode} ${error.description}",
                    )
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse,
                ) {
                    val frame = if (request.isForMainFrame) "main-frame" else "subframe"
                    logger.error(
                        "WebView HTTP error for $frame ${request.url}: " +
                            "${errorResponse.statusCode} ${errorResponse.reasonPhrase}",
                    )
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    logger.debug("Request to show fullscreen custom view")
                    if (!customViewSession.begin(callback)) {
                        logger.debug("Rejected fullscreen custom view because one is already active")
                        return
                    }

                    originalSystemUiVisibility = window.decorView.systemUiVisibility
                    customView = view
                    setContentView(
                        view,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        ),
                    )
                    hideSystemBars()
                    logger.debug("Fullscreen custom view shown")
                }

                override fun onHideCustomView() {
                    logger.debug("Request to hide fullscreen custom view")
                    hideCustomView()
                }
            }
        }

        setContentView(webView)
        hideSystemBars()
        logger.debug("Initial WebView content set; starting policy fetch")
        loadLexiaAfterPolicyFetch()
    }

    override fun onResume() {
        super.onResume()
        logger.debug("MainActivity onResume")
        if (this::webView.isInitialized) {
            webView.onResume()
            webView.resumeTimers()
        }
        hideSystemBars()
    }

    override fun onPause() {
        logger.debug("MainActivity onPause")
        if (this::webView.isInitialized) {
            webView.onPause()
            webView.pauseTimers()
        }
        super.onPause()
    }

    override fun onDestroy() {
        logger.debug("MainActivity onDestroy")
        isDestroyed = true
        if (this::webView.isInitialized) {
            hideCustomView()
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        logger.debug("Configuration changed: orientation=${newConfig.orientation}")
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        logger.debug("Window focus changed: hasFocus=$hasFocus")
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun configureFullscreenWindow() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
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

    @Suppress("DEPRECATION")
    private fun configureSettings(settings: WebSettings) {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        // Core5 serves its desktop experience based on user agent.
        settings.userAgentString = DESKTOP_USER_AGENT
    }

    private fun configureCookies(webView: WebView) {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            // Core5 authentication depends on cross-site MyLexia cookies.
            setAcceptThirdPartyCookies(webView, true)
        }
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
                    webView.loadUrl(LEXIA_CORE5_URL)
                } else {
                    logger.debug("Skipping Lexia load on UI thread because activity was destroyed")
                }
            }
        }.start()
    }

    private fun hideCustomView() {
        if (customView == null) {
            logger.debug("No fullscreen custom view to hide")
            return
        }

        customView = null
        window.decorView.systemUiVisibility = originalSystemUiVisibility
        setContentView(webView)
        hideSystemBars()
        customViewSession.finish()
        logger.debug("Fullscreen custom view hidden")
    }

    companion object {
        private const val LEXIA_CORE5_URL = "https://www.lexiacore5.com"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    }
}

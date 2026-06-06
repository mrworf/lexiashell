package nu.sensenet.lexiashell

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.Configuration
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
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var navigationPolicy = CspNavigationPolicy.fromCsp(null, LEXIA_CORE5_URL)
    @Volatile
    private var isDestroyed = false
    private var customView: View? = null
    private var originalSystemUiVisibility = 0

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    if (!request.isForMainFrame) {
                        return false
                    }

                    return !navigationPolicy.allows(request.url.toString())
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    if (customView != null) {
                        callback.onCustomViewHidden()
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
                }

                override fun onHideCustomView() {
                    customView = null
                    window.decorView.systemUiVisibility = originalSystemUiVisibility
                    setContentView(webView)
                    hideSystemBars()
                }
            }
        }

        setContentView(webView)
        hideSystemBars()
        loadLexiaAfterPolicyFetch()
    }

    override fun onResume() {
        super.onResume()
        if (this::webView.isInitialized) {
            webView.onResume()
            webView.resumeTimers()
        }
        hideSystemBars()
    }

    override fun onPause() {
        if (this::webView.isInitialized) {
            webView.onPause()
            webView.pauseTimers()
        }
        super.onPause()
    }

    override fun onDestroy() {
        isDestroyed = true
        if (this::webView.isInitialized) {
            if (customView != null) {
                customView = null
                setContentView(webView)
            }
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
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
            val fetchedPolicy = CspPolicyFetcher().fetch(LEXIA_CORE5_URL)
            if (isDestroyed) {
                return@Thread
            }

            runOnUiThread {
                if (!isDestroyed) {
                    navigationPolicy = fetchedPolicy
                    webView.loadUrl(LEXIA_CORE5_URL)
                }
            }
        }.start()
    }

    companion object {
        private const val LEXIA_CORE5_URL = "https://www.lexiacore5.com"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    }
}

package com.example.vvvip

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

class PlayerActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var cursorView: CursorView
    private lateinit var playerContainer: FrameLayout

    private var cursorX = 0f
    private var cursorY = 0f
    private val MOVE_STEP = 35f

    private val handler = Handler(Looper.getMainLooper())

    companion object {
        const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        private const val CURSOR_HIDE_DELAY = 5000L // 5秒无操作隐藏光标
    }

    private val hideCursorRunnable = Runnable {
        cursorView.visibility = View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        initViews()
        setupWebView()

        val playUrl = intent.getStringExtra("PLAY_URL")
        if (!playUrl.isNullOrEmpty()) {
            webView.loadUrl(playUrl)
        }

        playerContainer.post {
            cursorX = (playerContainer.width - cursorView.width) / 2f
            cursorY = (playerContainer.height - cursorView.height) / 2f
            updateCursorView()
        }

        resetCursorHideTimer()
    }

    private fun initViews() {
        playerContainer = findViewById(R.id.playerContainer)
        webView = findViewById(R.id.playerWebView)
        cursorView = findViewById(R.id.playerCursorView)

        webView.isFocusable = false
        webView.isFocusableInTouchMode = false
    }

    private fun resetCursorHideTimer() {
        cursorView.visibility = View.VISIBLE
        handler.removeCallbacks(hideCursorRunnable)
        handler.postDelayed(hideCursorRunnable, CURSOR_HIDE_DELAY)
    }

    private fun updateCursorView() {
        cursorX = cursorX.coerceIn(0f, playerContainer.width.toFloat() - cursorView.width)
        cursorY = cursorY.coerceIn(0f, playerContainer.height.toFloat() - cursorView.height)

        cursorView.x = cursorX
        cursorView.y = cursorY
        resetCursorHideTimer()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = DESKTOP_USER_AGENT

            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                injectDesktopNavigatorScript()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                return !url.startsWith("http://") && !url.startsWith("https://")
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                request?.requestHeaders?.apply {
                    put("User-Agent", DESKTOP_USER_AGENT)
                    put("Sec-Ch-Ua", "\"Chromium\";v=\"128\", \"Not;A=Brand\";v=\"24\", \"Google Chrome\";v=\"128\"")
                    put("Sec-Ch-Ua-Mobile", "?0")
                    put("Sec-Ch-Ua-Platform", "\"Windows\"")
                    put("Sec-Fetch-Dest", "document")
                    put("Sec-Fetch-Mode", "navigate")
                    put("Sec-Fetch-Site", "none")
                    put("Sec-Fetch-User", "?1")
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
    }

    private fun injectDesktopNavigatorScript() {
        val overrideScript = """
            (function() {
                try {
                    Object.defineProperty(navigator, 'platform', { get: function() { return 'Win32'; } });
                    Object.defineProperty(navigator, 'maxTouchPoints', { get: function() { return 0; } });
                    if (navigator.userAgentData) {
                        Object.defineProperty(navigator, 'userAgentData', {
                            get: function() {
                                return {
                                    brands: [
                                        { brand: 'Chromium', version: '128' },
                                        { brand: 'Not;A=Brand', version: '24' },
                                        { brand: 'Google Chrome', version: '128' }
                                    ],
                                    mobile: false,
                                    platform: 'Windows'
                                };
                            }
                        });
                    }
                } catch(e) {}
            })();
        """.trimIndent()
        webView.evaluateJavascript(overrideScript, null)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        resetCursorHideTimer()

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                cursorY -= MOVE_STEP
                updateCursorView()
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                cursorY += MOVE_STEP
                updateCursorView()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                cursorX -= MOVE_STEP
                updateCursorView()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                cursorX += MOVE_STEP
                updateCursorView()
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                performClickAtCursor()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                finish()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun performClickAtCursor() {
        cursorView.animatePressDown()
        cursorView.animatePressUp {
            val clickX = cursorX + cursorView.width / 2f
            val clickY = cursorY + cursorView.height / 2f

            val downTime = SystemClock.uptimeMillis()
            val eventTime = SystemClock.uptimeMillis()

            val webLocation = IntArray(2)
            webView.getLocationOnScreen(webLocation)
            val containerLocation = IntArray(2)
            playerContainer.getLocationOnScreen(containerLocation)

            val relativeX = clickX + (containerLocation[0] - webLocation[0])
            val relativeY = clickY + (containerLocation[1] - webLocation[1])

            val downEvent = MotionEvent.obtain(
                downTime, eventTime, MotionEvent.ACTION_DOWN, relativeX, relativeY, 0
            )
            val upEvent = MotionEvent.obtain(
                downTime, eventTime + 100, MotionEvent.ACTION_UP, relativeX, relativeY, 0
            )

            webView.dispatchTouchEvent(downEvent)
            webView.dispatchTouchEvent(upEvent)

            downEvent.recycle()
            upEvent.recycle()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(hideCursorRunnable)
        webView.destroy()
        super.onDestroy()
    }
}
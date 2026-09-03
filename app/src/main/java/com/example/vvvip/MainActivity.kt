package com.example.vvvip

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.tabs.TabLayout

class MainActivity : AppCompatActivity() {

    private enum class FocusMode { TAB_BAR, CURSOR }
    private var currentFocusMode = FocusMode.TAB_BAR

    private data class TabItem(val name: String, val url: String)
    private data class ParseApi(val name: String, val baseUrl: String)

    companion object {
        private val PLATFORM_TABS = listOf(
            TabItem("腾讯视频", "https://v.qq.com/channel/movie"),
            TabItem("爱奇艺视频", "https://www.iqiyi.com/movie/"),
            TabItem("优酷视频", "https://www.youku.com/ku/webmovie"),
            TabItem("B站视频", "https://www.bilibili.com/movie")
        )

        private val DEFAULT_PARSE_APIS = listOf(
            ParseApi("VIP视频解析线路一", "https://www.google.com"),
            ParseApi("VIP视频解析线路二", "https://www.baidu.com")
        )

        private const val PC_CHROME_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        private const val CURSOR_HIDE_DELAY = 60000L
    }

    private lateinit var tabLayout: TabLayout
    private lateinit var webView: WebView
    private lateinit var btnVipParse: Button
    private lateinit var cursorView: CursorView
    private lateinit var webContainer: FrameLayout

    private val parseApis = mutableListOf<ParseApi>()
    private var selectedParseIndex = 0

    private val handler = Handler(Looper.getMainLooper())
    private var isVideoPage = false

    private var cursorX = 0f
    private var cursorY = 0f
    private val MOVE_STEP = 35f

    // 遥控器按一次滚动的像素距离（数值越小越细腻）
    private val SCROLL_STEP = 150
    private val EDGE_THRESHOLD = 80f

    @Volatile
    private var isInputProcessing = false

    private val hideCursorRunnable = Runnable {
        cursorView.visibility = View.GONE
    }

    private val videoControlRunnable = object : Runnable {
        override fun run() {
            if (isVideoPage) {
                suppressWebVideoAndPIP()
            }
            handler.postDelayed(this, 300)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val rootView = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, statusBarHeight, 0, 0)
            insets
        }

        initData()
        initViews()
        setupWebView()
        setupTabs()

        webContainer.post {
            initCursorPosition()
            switchFocusMode(FocusMode.TAB_BAR)
        }

        handler.post(videoControlRunnable)
    }

    private fun initData() {
        val sp = getSharedPreferences("vvvip_config", Context.MODE_PRIVATE)
        parseApis.clear()
        parseApis.addAll(DEFAULT_PARSE_APIS)
        val savedIndex = sp.getInt("selected_parse_index", 0)
        selectedParseIndex = if (savedIndex in parseApis.indices) savedIndex else 0
    }

    private fun initViews() {
        tabLayout = findViewById(R.id.tabLayout)
        webView = findViewById(R.id.webView)
        btnVipParse = findViewById(R.id.btnVipParse)
        cursorView = findViewById(R.id.cursorView)
        webContainer = findViewById(R.id.webContainer)

        btnVipParse.setOnClickListener { showParseApiDialog() }

        webView.isFocusable = false
        webView.isFocusableInTouchMode = false

        webContainer.isFocusable = true
        webContainer.isFocusableInTouchMode = true
    }

    private fun resetCursorHideTimer() {
        handler.removeCallbacks(hideCursorRunnable)
        if (currentFocusMode == FocusMode.CURSOR) {
            cursorView.visibility = View.VISIBLE
            handler.postDelayed(hideCursorRunnable, CURSOR_HIDE_DELAY)
        }
    }

    private fun switchFocusMode(newMode: FocusMode) {
        currentFocusMode = newMode
        when (newMode) {
            FocusMode.TAB_BAR -> {
                handler.removeCallbacks(hideCursorRunnable)
                cursorView.visibility = View.GONE
                tabLayout.isFocusable = true
                tabLayout.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                tabLayout.requestFocus()
            }
            FocusMode.CURSOR -> {
                tabLayout.clearFocus()
                tabLayout.isFocusable = false
                tabLayout.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                webContainer.requestFocus()
                resetCursorHideTimer()
            }
        }
    }

    private fun initCursorPosition() {
        cursorX = (webContainer.width - cursorView.width) / 2f
        cursorY = (webContainer.height - cursorView.height) / 2f
        updateCursorView()
    }

    private fun updateCursorView() {
        cursorX = cursorX.coerceIn(0f, webContainer.width.toFloat() - cursorView.width)
        cursorY = cursorY.coerceIn(0f, webContainer.height.toFloat() - cursorView.height)

        cursorView.x = cursorX
        cursorView.y = cursorY
        resetCursorHideTimer()
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = true
            userAgentString = PC_CHROME_UA

            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }

        webView.addJavascriptInterface(
            WebJsInterface(
                onInputClick = { currentText ->
                    runOnUiThread {
                        if (isInputProcessing) return@runOnUiThread

                        isInputProcessing = true
                        showNativeInputDialog(currentText) { resultText ->
                            writeBackToWebInput(resultText)
                        }
                    }
                },
                onVideoPageDetected = { videoUrl ->
                    runOnUiThread { openPlayerActivity(videoUrl) }
                }
            ), "AndroidInterface"
        )

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let { checkUrlState(it) }
                injectDesktopNavigatorScript()
                if (isVideoPage) suppressWebVideoAndPIP()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let { checkUrlState(it) }

                enablePageZoomJS()
                injectInputInterceptorJS()
                injectVideoAndPipSuppressor()
                if (isVideoPage) suppressWebVideoAndPIP()
            }

            override fun onLoadResource(view: WebView?, url: String?) {
                super.onLoadResource(view, url)
                injectInputInterceptorJS()
                if (isVideoPage) suppressWebVideoAndPIP()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    return true
                }
                checkUrlState(url)
                return false
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                request?.requestHeaders?.apply {
                    put("User-Agent", PC_CHROME_UA)
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

        webView.webChromeClient = WebChromeClient()
    }

    private fun enablePageZoomJS() {
        val js = """
            (function() {
                var meta = document.querySelector('meta[name="viewport"]');
                if (meta) {
                    meta.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes');
                } else {
                    var newMeta = document.createElement('meta');
                    newMeta.name = 'viewport';
                    newMeta.content = 'width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes';
                    document.getElementsByTagName('head')[0].appendChild(newMeta);
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun setupTabs() {
        PLATFORM_TABS.forEach { tab ->
            tabLayout.addTab(tabLayout.newTab().setText(tab.name))
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.position?.let { index ->
                    if (index in PLATFORM_TABS.indices) {
                        webView.loadUrl(PLATFORM_TABS[index].url)
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                tab?.position?.let { index ->
                    if (index in PLATFORM_TABS.indices) {
                        webView.loadUrl(PLATFORM_TABS[index].url)
                    }
                }
            }
        })

        if (PLATFORM_TABS.isNotEmpty()) {
            webView.loadUrl(PLATFORM_TABS[0].url)
        }
    }

    private fun checkUrlState(url: String) {
        val isMatch = url.contains("/x/cover/") || url.contains("/play/") ||
                url.contains("/v_") || url.contains("/video/BV") ||
                url.contains("v.youku.com/v_show") || url.contains("iqiyi.com/v_")

        if (isMatch != isVideoPage) {
            isVideoPage = isMatch
            runOnUiThread {
                btnVipParse.visibility = if (isVideoPage) View.VISIBLE else View.GONE
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

    private fun injectInputInterceptorJS() {
        val currentUrl = webView.url ?: ""

        val js = """
            (function() {
                if (window.__inputInterceptorBound) return;
                window.__inputInterceptorBound = true;

                var currentUrl = '$currentUrl';

                function getPlatformInputNode(target) {
                    if (currentUrl.indexOf('iqiyi.com') !== -1) {
                        var iqiyiInput = document.querySelector('#iqiyi-search-input, input[data-search-input="true"]');
                        if (iqiyiInput) return iqiyiInput;
                        var box = target.closest('.search-box, .search-container, [class*="search"]');
                        if (box) return box.querySelector('input') || box;
                    }

                    if (currentUrl.indexOf('youku.com') !== -1) {
                        var youkuInput = document.querySelector('.search-box input, [class*="search"] input, input[type="search"]');
                        if (youkuInput) return youkuInput;
                        var ykContainer = target.closest('[class*="search"]');
                        if (ykContainer) return ykContainer.querySelector('input') || ykContainer;
                    }

                    if (currentUrl.indexOf('bilibili.com') !== -1) {
                        var biliInput = document.querySelector('.nav-search-input, input[type="search"], .search-input');
                        if (biliInput) return biliInput;
                    }

                    if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable) {
                        return target;
                    }
                    var parentInput = target.closest('input, textarea, [contenteditable="true"]');
                    if (parentInput) return parentInput;

                    return null;
                }

                function handleGlobalClick(e) {
                    var inputEl = getPlatformInputNode(e.target);
                    if (inputEl) {
                        try { inputEl.click(); inputEl.focus(); } catch(err) {}

                        setTimeout(function() {
                            var realInput = document.activeElement;
                            if (!realInput || (realInput.tagName !== 'INPUT' && realInput.tagName !== 'TEXTAREA' && !realInput.isContentEditable)) {
                                realInput = document.querySelector('#iqiyi-search-input, .search-box input, input[type="search"], input[type="text"]');
                            }

                            if (realInput) {
                                window.__lastActiveInput = realInput;
                                var val = realInput.value || realInput.innerText || realInput.textContent || '';
                                if (window.AndroidInterface && window.AndroidInterface.triggerInputPopup) {
                                    window.AndroidInterface.triggerInputPopup(val.trim());
                                }
                            }
                        }, 180);
                    }
                }

                document.addEventListener('click', handleGlobalClick, true);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun injectVideoAndPipSuppressor() {
        val js = """
            (function() {
                if (window.__observerInjected) return;
                window.__observerInjected = true;

                function cleanMediaAndPIP() {
                    var videos = document.querySelectorAll('video');
                    for (var i = 0; i < videos.length; i++) {
                        try {
                            videos[i].muted = true;
                            videos[i].pause();
                        } catch(e) {}
                    }

                    var pipSelectors = [
                        '.txp_mini_player', '.txp_none', '.txp_layer_mini',
                        '.iqp-mini-player', '#iqp-mini-player',
                        '.youku-film-mini-player', '.bilibili-player-video-mini',
                        '[class*="mini-player"]', '[class*="miniPlayer"]', '[id*="mini-player"]'
                    ];

                    pipSelectors.forEach(function(selector) {
                        var elements = document.querySelectorAll(selector);
                        elements.forEach(function(el) {
                            if (el && el.parentNode) {
                                el.parentNode.removeChild(el);
                            }
                        });
                    });
                }

                cleanMediaAndPIP();

                var observer = new MutationObserver(function(mutations) {
                    cleanMediaAndPIP();
                });

                observer.observe(document.body || document.documentElement, {
                    childList: true,
                    subtree: true
                });

                window.__cleanMediaAndPIP = cleanMediaAndPIP;
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun suppressWebVideoAndPIP() {
        webView.evaluateJavascript("if (window.__cleanMediaAndPIP) { window.__cleanMediaAndPIP(); } else { var vs=document.querySelectorAll('video'); for(var i=0;i<vs.length;i++){ vs[i].muted=true; vs[i].pause(); } }", null)
    }

    fun showNativeInputDialog(currentValue: String, onConfirm: (String) -> Unit) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
        }

        val etInput = EditText(this).apply {
            setText(currentValue)
            hint = "请输入关键字进行搜索..."
            inputType = InputType.TYPE_CLASS_TEXT
            maxLines = 1
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
        }
        container.addView(etInput)

        val dialog = AlertDialog.Builder(this)
            .setTitle("键盘输入")
            .setView(container)
            .setCancelable(true)
            .setPositiveButton("确定") { dialogInterface, _ ->
                val resultText = etInput.text.toString().trim()
                onConfirm(resultText)
                dialogInterface.dismiss()
            }
            .setNegativeButton("取消") { dialogInterface, _ ->
                dialogInterface.dismiss()
                unlockInputStateWithDelay()
            }
            .setOnCancelListener {
                unlockInputStateWithDelay()
            }
            .create()

        dialog.setOnShowListener {
            etInput.postDelayed({
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(etInput, InputMethodManager.SHOW_IMPLICIT)
            }, 150)
        }

        dialog.show()
    }

    private fun writeBackToWebInput(value: String) {
        val safeValue = value.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", " ")
        val currentUrl = webView.url ?: ""

        val js = """
            (function() {
                var currentUrl = '$currentUrl';

                function getTargetNode() {
                    if (window.__lastActiveInput && document.body.contains(window.__lastActiveInput)) {
                        return window.__lastActiveInput;
                    }
                    if (currentUrl.indexOf('iqiyi.com') !== -1) {
                        return document.querySelector('#iqiyi-search-input, input[data-search-input="true"], .search-box input');
                    }
                    if (currentUrl.indexOf('youku.com') !== -1) {
                        return document.querySelector('.search-box input, [class*="search"] input, input[type="search"]');
                    }
                    if (currentUrl.indexOf('bilibili.com') !== -1) {
                        return document.querySelector('.nav-search-input, input[type="search"], .search-input');
                    }
                    return document.querySelector('input[type="search"], input[type="text"], input:not([type])');
                }

                var el = getTargetNode();
                if (!el) return;

                try { el.focus(); } catch(e) {}

                if (currentUrl.indexOf('iqiyi.com') !== -1) {
                    el.value = '$safeValue';
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                    
                    setTimeout(function() {
                        var btn = document.querySelector('#iqiyi-search-btn, .search-btn, [class*="search-btn"]');
                        if (btn) try { btn.click(); } catch(e) {}
                    }, 100);
                }

                else if (currentUrl.indexOf('youku.com') !== -1) {
                    try { el.value = ''; } catch(e) {}
                    var success = false;
                    try { success = document.execCommand('insertText', false, '$safeValue'); } catch(e) {}
                    
                    if (!success || el.value !== '$safeValue') {
                        var proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
                        var setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
                        setter.call(el, '$safeValue');
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                    }

                    setTimeout(function() {
                        var btn = document.querySelector('[class*="search-btn"], [class*="search-button"], .search-icon');
                        if (btn) try { btn.click(); } catch(e) {}
                    }, 100);
                }

                else if (currentUrl.indexOf('bilibili.com') !== -1) {
                    var proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
                    var setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
                    setter.call(el, '$safeValue');

                    try {
                        el.dispatchEvent(new CompositionEvent('compositionstart', { bubbles: true }));
                        el.dispatchEvent(new CompositionEvent('compositionend', { bubbles: true, data: '$safeValue' }));
                    } catch(e) {}

                    el.dispatchEvent(new Event('input', { bubbles: true, cancelable: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true, cancelable: true }));

                    setTimeout(function() {
                        var kbOpts = { bubbles: true, cancelable: true, keyCode: 13, which: 13, key: 'Enter' };
                        el.dispatchEvent(new KeyboardEvent('keydown', kbOpts));
                        el.dispatchEvent(new KeyboardEvent('keyup', kbOpts));
                    }, 100);
                }

                else {
                    var proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
                    var descriptor = Object.getOwnPropertyDescriptor(proto, 'value');
                    if (descriptor && descriptor.set) {
                        descriptor.set.call(el, '$safeValue');
                    } else {
                        el.value = '$safeValue';
                    }

                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));

                    setTimeout(function() {
                        var kbOpts = { bubbles: true, cancelable: true, keyCode: 13, which: 13, key: 'Enter' };
                        el.dispatchEvent(new KeyboardEvent('keydown', kbOpts));
                        el.dispatchEvent(new KeyboardEvent('keyup', kbOpts));
                    }, 100);
                }

                window.__lastActiveInput = null;
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) {
            handler.postDelayed({
                switchFocusMode(FocusMode.CURSOR)
                unlockInputStateWithDelay()
            }, 200)
        }
    }

    private fun unlockInputStateWithDelay() {
        handler.postDelayed({
            isInputProcessing = false
        }, 500)
    }

    private fun showParseApiDialog() {
        val items = parseApis.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择 VIP 解析线路")
            .setSingleChoiceItems(items, selectedParseIndex) { _, which ->
                selectedParseIndex = which
                getSharedPreferences("vvvip_config", Context.MODE_PRIVATE)
                    .edit()
                    .putInt("selected_parse_index", which)
                    .apply()
            }
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { dialog, _ ->
                dialog.dismiss()
                val currentWebUrl = webView.url ?: return@setPositiveButton
                openPlayerActivity(currentWebUrl)
            }
            .show()
    }

    fun openPlayerActivity(videoUrl: String) {
        val selectedParseApi = parseApis[selectedParseIndex].baseUrl
        val finalUrl = selectedParseApi + videoUrl

        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("PLAY_URL", finalUrl)
        }
        startActivity(intent)
    }

    private fun scrollWebPage(yOffset: Int, onComplete: ((Boolean) -> Unit)? = null) {
        val clickX = cursorX + cursorView.width / 2f
        val clickY = cursorY + cursorView.height / 2f

        val webLocation = IntArray(2)
        webView.getLocationOnScreen(webLocation)
        val containerLocation = IntArray(2)
        webContainer.getLocationOnScreen(containerLocation)

        val relativeX = clickX + (containerLocation[0] - webLocation[0])
        val relativeY = clickY + (containerLocation[1] - webLocation[1])

        val js = """
            (function(x, y, offset) {
                var mainSelectors = ['#app', '#root', '#__next', '#iQIYI-main', 'main', '.page-wrapper', '.layout-main'];
                var mainAppEl = null;

                for (var i = 0; i < mainSelectors.length; i++) {
                    var el = document.querySelector(mainSelectors[i]);
                    if (el && el.scrollHeight > el.clientHeight) {
                        mainAppEl = el;
                        break;
                    }
                }

                if (mainAppEl) {
                    var prev = mainAppEl.scrollTop;
                    mainAppEl.scrollBy({ top: offset, behavior: 'smooth' });
                    // 如果 scrollTop 发生变化，说明成功滚动
                    if (Math.abs(mainAppEl.scrollTop - prev) > 0.5) {
                        return { scrolled: true, atTop: mainAppEl.scrollTop <= 0 };
                    }
                }

                var midX = window.innerWidth / 2;
                var midY = window.innerHeight / 2;
                var target = document.elementFromPoint(midX, midY) || document.elementFromPoint(x, y);

                var curr = target;
                while (curr && curr !== document.body && curr !== document.documentElement) {
                    var style = window.getComputedStyle(curr);
                    var overflowY = style.overflowY;
                    if ((overflowY === 'auto' || overflowY === 'scroll' || overflowY === 'overlay') && curr.scrollHeight > curr.clientHeight) {
                        var oldScroll = curr.scrollTop;
                        curr.scrollBy({ top: offset, behavior: 'smooth' });
                        if (Math.abs(curr.scrollTop - oldScroll) > 0.5) {
                            return { scrolled: true, atTop: curr.scrollTop <= 0 };
                        }
                    }
                    curr = curr.parentElement;
                }

                var winOld = window.scrollY || document.documentElement.scrollTop || document.body.scrollTop || 0;
                window.scrollBy({ top: offset, behavior: 'smooth' });
                document.documentElement.scrollBy({ top: offset, behavior: 'smooth' });
                document.body.scrollBy({ top: offset, behavior: 'smooth' });
                
                var winNew = window.scrollY || document.documentElement.scrollTop || document.body.scrollTop || 0;
                return { scrolled: Math.abs(winNew - winOld) > 0.5, atTop: winNew <= 0 };
            })($relativeX, $relativeY, $yOffset);
        """.trimIndent()

        webView.evaluateJavascript(js) { resultJson ->
            val isAtTop = resultJson?.contains("\"atTop\":true") == true
            onComplete?.invoke(isAtTop)
        }
    }

    private fun fetchPageScrollY(onResult: (Int) -> Unit) {
        onResult(webView.scrollY)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        resetCursorHideTimer()

        when (currentFocusMode) {
            FocusMode.TAB_BAR -> {
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    switchFocusMode(FocusMode.CURSOR)
                    return true
                }
            }

            FocusMode.CURSOR -> {
                val containerHeight = webContainer.height.toFloat()

                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (cursorY <= EDGE_THRESHOLD) {
                            fetchPageScrollY { pageScrollY ->
                                if (pageScrollY > 10 || webView.scrollY > 0) {
                                    scrollWebPage(-SCROLL_STEP)
                                } else {
                                    if (cursorY <= 5f) {
                                        switchFocusMode(FocusMode.TAB_BAR)
                                    } else {
                                        cursorY -= MOVE_STEP
                                        updateCursorView()
                                    }
                                }
                            }
                        } else {
                            cursorY -= MOVE_STEP
                            updateCursorView()
                        }
                        return true
                    }

                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (cursorY >= containerHeight - cursorView.height - EDGE_THRESHOLD) {
                            scrollWebPage(SCROLL_STEP)
                        } else {
                            cursorY += MOVE_STEP
                            updateCursorView()
                        }
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
                        if (webView.canGoBack()) {
                            webView.goBack()
                            return true
                        } else {
                            switchFocusMode(FocusMode.TAB_BAR)
                            return true
                        }
                    }
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun performClickAtCursor() {
        cursorView.animatePressDown()

        cursorView.animatePressUp {
            val clickX = cursorX + cursorView.width / 2f
            val clickY = cursorY + cursorView.height / 2f

            if (btnVipParse.visibility == View.VISIBLE) {
                val rect = Rect()
                btnVipParse.getHitRect(rect)
                if (rect.contains(clickX.toInt(), clickY.toInt())) {
                    btnVipParse.performClick()
                    return@animatePressUp
                }
            }

            val downTime = SystemClock.uptimeMillis()
            val eventTime = SystemClock.uptimeMillis()

            val webLocation = IntArray(2)
            webView.getLocationOnScreen(webLocation)
            val containerLocation = IntArray(2)
            webContainer.getLocationOnScreen(containerLocation)

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
        handler.removeCallbacks(videoControlRunnable)
        handler.removeCallbacks(hideCursorRunnable)
        webView.destroy()
        super.onDestroy()
    }
}
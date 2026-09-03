package com.example.vvvip

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.fragment.app.Fragment

class WebFragment : Fragment() {

    private lateinit var webView: WebView
    private var initialUrl: String = ""

    companion object {
        fun newInstance(url: String): WebFragment {
            val fragment = WebFragment()
            val args = Bundle()
            args.putString("ARG_URL", url)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialUrl = arguments?.getString("ARG_URL") ?: ""
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        webView = WebView(requireContext())
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        // 模拟 Desktop 或移动 UserAgent
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
        settings.mediaPlaybackRequiresUserGesture = false

        // 注册原生的 JS Interface 回调方法
        webView.addJavascriptInterface(
            WebJsInterface(
                onInputClick = { currentValue ->
                    activity?.runOnUiThread {
                        (activity as? MainActivity)?.showNativeInputDialog(currentValue) { resultText ->
                            webView.evaluateJavascript("window.fillNativeInput('$resultText');", null)
                        }
                    }
                },
                onVideoPageDetected = { videoUrl ->
                    activity?.runOnUiThread {
                        (activity as? MainActivity)?.openPlayerActivity(videoUrl)
                    }
                }
            ),
            "AndroidInterface"
        )

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // 3. 在页面加载阶段持续注入暂停视频与销毁 PIP 的脚本
                webView.evaluateJavascript(WebJsInterface.INJECT_SCRIPT, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                webView.evaluateJavascript(WebJsInterface.INJECT_SCRIPT, null)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                // 判断如果是二级视频详情页，可以直接走 Native 拦截播放
                if (isDetailPage(url)) {
                    (activity as? MainActivity)?.openPlayerActivity(url)
                    return true
                }
                return false
            }
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                // 给所有 HTML/JS 请求追加 Chrome PC 特有的 Client Hints 标头
                request?.requestHeaders?.apply {
                    put("Sec-Ch-Ua", "\"Chromium\";v=\"151\", \"Not;A=Brand\";v=\"24\", \"Google Chrome\";v=\"151\"")
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

        webView.loadUrl(initialUrl)
        return webView
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
                                        { brand: 'Chromium', version: '151' },
                                        { brand: 'Not;A=Brand', version: '24' },
                                        { brand: 'Google Chrome', version: '151' }
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

    private fun isDetailPage(url: String): Boolean {
        // 粗略匹配主流视频平台详情/播放页特征
        return url.contains("/cover/") || url.contains("/play/") || url.contains("v_") || url.contains("/bangumi/play/")
    }

    fun getCurrentUrl(): String? = webView.url

    fun canGoBack(): Boolean = webView.canGoBack()

    fun goBack() {
        if (webView.canGoBack()) {
            webView.goBack()
        }
    }

    override fun onDestroyView() {
        webView.destroy()
        super.onDestroyView()
    }
}
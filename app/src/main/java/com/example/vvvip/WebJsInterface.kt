package com.example.vvvip

import android.webkit.JavascriptInterface

class WebJsInterface(
    private val onInputClick: (currentText: String) -> Unit,
    private val onVideoPageDetected: (url: String) -> Unit
) {
    @JavascriptInterface
    fun triggerNativeInput(currentValue: String) {
        onInputClick(currentValue)
    }

    @JavascriptInterface
    fun onVideoClick(url: String) {
        onVideoPageDetected(url)
    }

    @JavascriptInterface
    fun triggerInputPopup(currentText: String) {
        onInputClick(currentText)
    }

    @JavascriptInterface
    fun onVideoFound(url: String) {
        onVideoPageDetected(url)
    }

    companion object {
        // 用于在 WebView 注入的全局 JS 脚本
        val INJECT_SCRIPT = """
            (function() {
                // 1. 轮询暂停、静音并退出 Picture-in-Picture
                setInterval(function() {
                    var videos = document.getElementsByTagName('video');
                    for (var i = 0; i < videos.length; i++) {
                        var v = videos[i];
                        if (!v.paused) {
                            v.pause();
                        }
                        v.muted = true;
                        v.volume = 0;
                        if (document.pictureInPictureElement) {
                            document.exitPictureInPicture().catch(function(e){});
                        }
                    }
                }, 500);

                // 2. 拦截输入框点击，防止失焦无法弹键盘
                document.addEventListener('click', function(e) {
                    var target = e.target;
                    if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA')) {
                        // 阻止默认点击弹窗逻辑，拉起 Android 原生输入框
                        e.stopPropagation();
                        window.AndroidInterface.triggerNativeInput(target.value || '');
                        window.currentActiveInput = target;
                    }
                }, true);

                // 3. 全局 JS 方法：由 Native 赋值回 Web 输入框并触发 submit/change 事件
                window.fillNativeInput = function(text) {
                    if (window.currentActiveInput) {
                        window.currentActiveInput.value = text;
                        window.currentActiveInput.dispatchEvent(new Event('input', { bubbles: true }));
                        window.currentActiveInput.dispatchEvent(new Event('change', { bubbles: true }));
                    }
                };
            })();
        """.trimIndent()
    }
}
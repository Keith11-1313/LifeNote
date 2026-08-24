package com.lifenote

import android.app.Activity
import android.os.Bundle
import android.webkit.WebView

class MainActivity : Activity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        setContentView(webView)
        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onBackPressed() {
        // UI overlays (editor/reader) consume back via JS; otherwise default exit.
        webView.evaluateJavascript(
            "(typeof handleAndroidBack === 'function') ? String(handleAndroidBack()) : 'false';"
        ) { result ->
            if (result != "\"true\"") {
                super.onBackPressed()
            }
        }
    }
}

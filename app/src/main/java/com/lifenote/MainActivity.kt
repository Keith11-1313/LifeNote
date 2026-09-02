package com.lifenote

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import org.json.JSONObject
import java.time.LocalDate

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var settings: Settings
    private lateinit var store: JournalStore
    private lateinit var history: HistoryStore
    private lateinit var archiveManager: ArchiveManager
    private var server: HttpServer? = null
    private var opened = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings.load(this)
        store = JournalStore(java.io.File(filesDir, "journal"))
        history = HistoryStore(java.io.File(filesDir, "journal/.history"), store)
        archiveManager = ArchiveManager(filesDir, store)

        openJournal()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun openJournal() {
        opened = true
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.addJavascriptInterface(FilesBridge(), "LifeNoteFiles")
        webView.addJavascriptInterface(SecurityBridge(), "LifeNoteSecurity")
        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(
                view: WebView?, url: String?, message: String?, result: JsResult?
            ): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message ?: "")
                    .setPositiveButton("OK") { _, _ -> result?.confirm() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            override fun onJsConfirm(
                view: WebView?, url: String?, message: String?, result: JsResult?
            ): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message ?: "")
                    .setPositiveButton("OK") { _, _ -> result?.confirm() }
                    .setNegativeButton("Cancel") { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

        }
        setContentView(webView)
        startServer()
        webView.loadUrl("http://127.0.0.1:8420/")
    }

    override fun onResume() {
        super.onResume()
        if (opened) {
            startServer()
            webView.post {
                webView.evaluateJavascript(
                    "if (typeof onNativeResume === 'function') onNativeResume();",
                    null
                )
            }
        }
    }

    private fun startServer() {
        if (server != null) return
        val s = HttpServer(8420, store, history, settings, assets)
        s.start()
        server = s
    }

    private inner class FilesBridge {
        @JavascriptInterface
        fun exportJournal() = runOnUiThread {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"
                putExtra(Intent.EXTRA_TITLE, "LifeNote-${LocalDate.now()}.zip")
            }
            openDocumentPicker(intent, REQUEST_EXPORT, "Choose where to save the backup")
        }

        @JavascriptInterface
        fun importJournal(mode: String) = runOnUiThread {
            val request = if (mode == "replace") REQUEST_IMPORT_REPLACE else REQUEST_IMPORT_MERGE
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            openDocumentPicker(intent, request, "Choose a LifeNote backup")
        }
    }

    private fun openDocumentPicker(intent: Intent, request: Int, message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        runCatching { startActivityForResult(intent, request) }
            .onFailure { notifyArchiveResult(false, "Android could not open the file picker", false) }
    }

    private inner class SecurityBridge {
        @JavascriptInterface
        fun hasPassword(): Boolean = settings.hasLockPin

        @JavascriptInterface
        fun verifyPassword(password: String): Boolean = settings.matchesLockPin(password)

        @JavascriptInterface
        fun setPassword(current: String, replacement: String): String {
            if (replacement.length < 4) return "Use at least 4 characters"
            if (settings.hasLockPin && !settings.matchesLockPin(current)) return "Current password is incorrect"
            settings.setLockPin(replacement)
            return "ok"
        }

        @JavascriptInterface
        fun removePassword(current: String): Boolean {
            if (!settings.hasLockPin || !settings.matchesLockPin(current)) return false
            settings.removeLockPin()
            return true
        }

        @JavascriptInterface
        fun exitApp() = runOnUiThread { moveTaskToBack(true) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (opened) startServer()
        if (resultCode != RESULT_OK) {
            if (requestCode in REQUEST_EXPORT..REQUEST_IMPORT_REPLACE) {
                notifyArchiveResult(false, "Backup action canceled", false)
            }
            return
        }
        val uri = data?.data ?: run {
            notifyArchiveResult(false, "Android did not return a backup file", false)
            return
        }
        Thread {
            runCatching {
                when (requestCode) {
                    REQUEST_EXPORT -> {
                        val count = contentResolver.openOutputStream(uri, "w")!!.use { archiveManager.exportTo(it) }
                        "Exported $count ${if (count == 1) "entry" else "entries"}"
                    }
                    REQUEST_IMPORT_MERGE, REQUEST_IMPORT_REPLACE -> {
                        val mode = if (requestCode == REQUEST_IMPORT_REPLACE) {
                            ArchiveManager.ImportMode.REPLACE
                        } else ArchiveManager.ImportMode.MERGE
                        val result = contentResolver.openInputStream(uri)!!.use { archiveManager.importFrom(it, mode) }
                        if (mode == ArchiveManager.ImportMode.REPLACE) {
                            "Replaced journal with ${result.imported} ${if (result.imported == 1) "entry" else "entries"}"
                        } else "Imported ${result.imported}; kept ${result.kept} existing"
                    }
                    else -> return@Thread
                }
            }.onSuccess { message -> notifyArchiveResult(true, message, requestCode != REQUEST_EXPORT) }
                .onFailure { error -> notifyArchiveResult(false, error.message ?: "Backup operation failed", false) }
        }.start()
    }

    private fun notifyArchiveResult(success: Boolean, message: String, reload: Boolean) = runOnUiThread {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        val script = "window.onNativeArchiveResult(${success},${JSONObject.quote(message)},${reload});"
        webView.evaluateJavascript(script, null)
    }

    override fun onPause() {
        super.onPause()
        server?.stop()
        server = null
    }

    override fun onBackPressed() {
        if (!opened) { moveTaskToBack(true); return }
        webView.evaluateJavascript(
            "(typeof handleAndroidBack === 'function') ? String(handleAndroidBack()) : 'false';"
        ) { result ->
            if (result != "\"true\"") {
                super.onBackPressed()
            }
        }
    }

    companion object {
        private const val REQUEST_EXPORT = 2001
        private const val REQUEST_IMPORT_MERGE = 2002
        private const val REQUEST_IMPORT_REPLACE = 2003
    }
}

package com.lifenote

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.EditText
import android.text.InputType
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
    private var unlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings.load(this)
        store = JournalStore(java.io.File(filesDir, "journal"))
        history = HistoryStore(java.io.File(filesDir, "journal/.history"), store)
        archiveManager = ArchiveManager(filesDir, store)

        showLockScreen()
    }

    private fun showLockScreen() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = if (settings.hasLockPin) "Enter PIN" else "Choose a PIN (4+ digits)"
        }
        AlertDialog.Builder(this)
            .setTitle(if (settings.hasLockPin) "LifeNote is locked" else "Protect LifeNote")
            .setMessage(if (settings.hasLockPin) "Enter your PIN to open your journal." else "Choose a PIN before your journal is shown.")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Continue", null)
            .show().also { dialog ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val pin = input.text.toString()
                    if (pin.length < 4) { input.error = "Use at least 4 digits"; return@setOnClickListener }
                    if (!settings.hasLockPin) {
                        settings.setLockPin(pin)
                        openJournal()
                        dialog.dismiss()
                    } else if (settings.matchesLockPin(pin)) {
                        openJournal()
                        dialog.dismiss()
                    } else input.error = "Incorrect PIN"
                }
            }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun openJournal() {
        unlocked = true
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.addJavascriptInterface(FilesBridge(), "LifeNoteFiles")
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

            override fun onJsPrompt(
                view: WebView?, url: String?, message: String?,
                defaultValue: String?, result: JsPromptResult?
            ): Boolean {
                val input = EditText(this@MainActivity)
                input.setText(defaultValue ?: "")
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(message ?: "LifeNote")
                    .setView(input)
                    .setPositiveButton("OK") { _, _ -> result?.confirm(input.text.toString()) }
                    .setNegativeButton("Cancel") { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }
        }
        setContentView(webView)
        startServer()
        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onResume() {
        super.onResume()
        if (unlocked) startServer()
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
            startActivityForResult(intent, REQUEST_EXPORT)
        }

        @JavascriptInterface
        fun importJournal(mode: String) = runOnUiThread {
            val request = if (mode == "replace") REQUEST_IMPORT_REPLACE else REQUEST_IMPORT_MERGE
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"
            }
            startActivityForResult(intent, request)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
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
        val script = "window.onNativeArchiveResult(${success},${JSONObject.quote(message)},${reload});"
        webView.evaluateJavascript(script, null)
    }

    override fun onPause() {
        super.onPause()
        server?.stop()
        server = null
    }

    override fun onBackPressed() {
        if (!unlocked) { moveTaskToBack(true); return }
        // UI overlays (editor/reader) consume back via JS; otherwise default exit.
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

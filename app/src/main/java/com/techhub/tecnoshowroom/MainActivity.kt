package com.techhub.tecnoshowroom

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.provider.OpenableColumns
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.util.Base64
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(DownloadBridge(), "Android")
        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun js(fn: String, arg: String) {
        webView.evaluateJavascript("$fn(" + org.json.JSONObject.quote(arg) + ")", null)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == 4711 && resultCode == RESULT_OK && uri != null) {
            try {
                var name = "file"
                contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && i >= 0) name = c.getString(i)
                }
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                webView.evaluateJavascript("onFilePicked(" + org.json.JSONObject.quote(name) + "," + org.json.JSONObject.quote(b64) + ")", null)
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Could not read file", Toast.LENGTH_LONG).show() }
            }
        }
    }

    inner class DownloadBridge {
        @JavascriptInterface
        fun pickFile() {
            runOnUiThread {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                startActivityForResult(i, 4711)
            }
        }

        @JavascriptInterface
        fun saveBackup(filename: String, text: String) {
            try {
                val safeName = filename.substringAfterLast('/').substringAfterLast('\\')
                val bytes = text.toByteArray(Charsets.UTF_8)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                        put(MediaStore.Downloads.MIME_TYPE, "application/json")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TECNO Showroom")
                    }
                    val uri: Uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw Exception("Unable to create backup")
                    contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: throw Exception("Unable to write backup")
                    runOnUiThread {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(send, "Save backup to Drive / Gmail"))
                    }
                } else {
                    val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
                    FileOutputStream(File(dir, safeName)).use { it.write(bytes) }
                    runOnUiThread { Toast.makeText(this@MainActivity, "Backup saved in app Downloads", Toast.LENGTH_LONG).show() }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Backup failed: " + e.message, Toast.LENGTH_LONG).show() }
            }
        }

        @JavascriptInterface
        fun scan() {
            runOnUiThread {
                try {
                    GmsBarcodeScanning.getClient(this@MainActivity).startScan()
                        .addOnSuccessListener { b -> js("onScan", b.rawValue ?: "") }
                        .addOnCanceledListener { js("onScanEnd", "") }
                        .addOnFailureListener { e -> js("onScanFail", e.message ?: "") }
                } catch (e: Exception) {
                    js("onScanFail", e.message ?: "")
                }
            }
        }

        @JavascriptInterface
        fun saveExcel(filename: String, base64: String) {
            try {
                val safeName = filename.substringAfterLast('/').substringAfterLast('\\')
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                val mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                        put(MediaStore.Downloads.MIME_TYPE, mime)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TECNO Showroom")
                    }
                    val uri: Uri? = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri == null) throw Exception("Unable to create download")
                    contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: throw Exception("Unable to write download")
                } else {
                    val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
                    FileOutputStream(File(dir, safeName)).use { it.write(bytes) }
                }
                runOnUiThread { Toast.makeText(this@MainActivity, "Excel saved to Downloads", Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }
}

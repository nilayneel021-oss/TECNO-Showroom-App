package com.techhub.tecnoshowroom

import android.app.Activity
import android.content.ContentValues
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

    inner class DownloadBridge {
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

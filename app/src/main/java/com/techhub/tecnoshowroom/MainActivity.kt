package com.techhub.tecnoshowroom

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
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

    private val driveScope = "https://www.googleapis.com/auth/drive.appdata"

    private fun jsUi(fn: String, arg: String) {
        runOnUiThread { webView.evaluateJavascript(fn + "(" + org.json.JSONObject.quote(arg) + ")", null) }
    }

    private fun withToken(onToken: (String) -> Unit, onFail: (String) -> Unit, allowUi: Boolean) {
        val req = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(driveScope))).build()
        Identity.getAuthorizationClient(this).authorize(req)
            .addOnSuccessListener { r ->
                if (r.hasResolution()) {
                    if (allowUi) {
                        try {
                            startIntentSenderForResult(r.pendingIntent!!.intentSender, 4712, null, 0, 0, 0)
                        } catch (e: Exception) {
                            onFail(e.message ?: "authorization failed")
                        }
                    } else {
                        onFail("Reconnect Google Drive in Settings")
                    }
                } else {
                    onToken(r.accessToken ?: "")
                }
            }
            .addOnFailureListener { e -> onFail(e.message ?: "authorization failed") }
    }

    private fun httpCall(method: String, url: String, tok: String, ctype: String?, body: ByteArray?): String {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 20000
        c.readTimeout = 60000
        c.setRequestProperty("Authorization", "Bearer " + tok)
        if (body != null) {
            c.doOutput = true
            if (ctype != null) c.setRequestProperty("Content-Type", ctype)
            c.outputStream.use { it.write(body) }
        }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        if (code !in 200..299) throw Exception("Drive error " + code + ": " + text.take(100))
        return text
    }

    private fun driveIds(tok: String, name: String): List<String> {
        val q = java.net.URLEncoder.encode("name='" + name + "'", "UTF-8")
        val r = org.json.JSONObject(httpCall("GET", "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&orderBy=modifiedTime%20desc&fields=files(id)&q=" + q, tok, null, null))
        val a = r.optJSONArray("files")
        val out = ArrayList<String>()
        if (a != null) for (i in 0 until a.length()) out.add(a.getJSONObject(i).getString("id"))
        return out
    }

    private fun driveSave(tok: String, name: String, text: String) {
        val old = driveIds(tok, name)
        val b = "tecnoBoundary7391"
        val meta = org.json.JSONObject().put("name", name).put("parents", org.json.JSONArray().put("appDataFolder")).toString()
        val body = ("--" + b + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" + meta + "\r\n--" + b + "\r\nContent-Type: application/json\r\n\r\n" + text + "\r\n--" + b + "--").toByteArray(Charsets.UTF_8)
        httpCall("POST", "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart", tok, "multipart/related; boundary=" + b, body)
        for (id in old) {
            try { httpCall("DELETE", "https://www.googleapis.com/drive/v3/files/" + id, tok, null, null) } catch (e: Exception) { }
        }
    }

    private fun driveLoad(tok: String, name: String): String? {
        val ids = driveIds(tok, name)
        if (ids.isEmpty()) return null
        return httpCall("GET", "https://www.googleapis.com/drive/v3/files/" + ids[0] + "?alt=media", tok, null, null)
    }

    override fun onPause() {
        super.onPause()
        if (::webView.isInitialized) webView.evaluateJavascript("if(window.driveFlush)driveFlush()", null)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 4712) {
            if (resultCode == RESULT_OK && data != null) {
                try {
                    Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data)
                    jsUi("onDriveConnected", "")
                } catch (e: Exception) {
                    jsUi("onDriveFail", e.message ?: "authorization failed")
                }
            } else {
                jsUi("onDriveFail", "Cancelled")
            }
            return
        }
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
        fun driveConnect() {
            runOnUiThread {
                withToken({ _ -> jsUi("onDriveConnected", "") }, { m -> jsUi("onDriveFail", m) }, true)
            }
        }

        @JavascriptInterface
        fun driveUpload(filename: String, text: String) {
            runOnUiThread {
                withToken({ tok ->
                    Thread {
                        try {
                            driveSave(tok, filename, text)
                            jsUi("onDriveResult", "ok")
                        } catch (e: Exception) {
                            jsUi("onDriveResult", "ERR:" + (e.message ?: "upload failed"))
                        }
                    }.start()
                }, { m -> jsUi("onDriveResult", "ERR:" + m) }, false)
            }
        }

        @JavascriptInterface
        fun driveRestore(filename: String) {
            runOnUiThread {
                withToken({ tok ->
                    Thread {
                        try {
                            val t = driveLoad(tok, filename)
                            if (t == null) jsUi("onDriveFail", "No backup found in Drive") else jsUi("onDriveRestore", t)
                        } catch (e: Exception) {
                            jsUi("onDriveFail", e.message ?: "restore failed")
                        }
                    }.start()
                }, { m -> jsUi("onDriveFail", m) }, true)
            }
        }

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

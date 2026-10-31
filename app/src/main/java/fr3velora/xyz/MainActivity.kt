package fr3velora.xyz

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.app.NotificationManagerCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.json.JSONObject
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var fabMenu: ImageButton
    private var listenerPrompted = false

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var lastKeyFetch = 0L
    private var loadedBase = ""

    private val fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uris = if (res.resultCode == Activity.RESULT_OK)
            WebChromeClient.FileChooserParams.parseResult(res.resultCode, res.data) else null
        fileCallback?.onReceiveValue(uris)
        fileCallback = null
    }

    private val base: String get() = Prefs.baseUrl(this)

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        swipe   = findViewById(R.id.swipe)
        fabMenu = findViewById(R.id.fabMenu)

        // ── WebView ──
        webView.setBackgroundColor(0xFF0A0D13.toInt())
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = true
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = userAgentString + " Fr3veloraApp/" + BuildConfig.VERSION_NAME
        }
        CookieManager.getInstance().setAcceptCookie(true)
        webView.addJavascriptInterface(Bridge(), "Fr3veloraApp")
        webView.webViewClient = Client()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView?, callback: ValueCallback<Array<Uri>>, params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try { fileChooser.launch(params.createIntent()); true }
                catch (_: Exception) { fileCallback = null; false }
            }
        }
        webView.setDownloadListener(DownloadListener { url, _, contentDisposition, mime, _ ->
            handleDownload(url, contentDisposition, mime)
        })

        swipe.setOnRefreshListener { webView.reload() }
        swipe.setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 }

        if (savedInstanceState != null) webView.restoreState(savedInstanceState)
        else if (!handleIntent(intent)) loadHome()

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
        }

        // ── FAB draggable + klik tampilkan menu ──
        setupFab()
    }

    // ── FAB: drag bebas + klik buka popup menu ──

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFab() {
        var downX = 0f; var downY = 0f
        var lastX = 0f; var lastY = 0f
        var moved = false

        fabMenu.setOnTouchListener { v, event ->
            val parent = v.parent as View
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    lastX = v.x;       lastY = v.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!moved && (Math.abs(dx) < 8 && Math.abs(dy) < 8)) return@setOnTouchListener true
                    moved = true
                    val newX = (lastX + dx).coerceIn(0f, (parent.width  - v.width ).toFloat())
                    val newY = (lastY + dy).coerceIn(0f, (parent.height - v.height).toFloat())
                    v.x = newX; v.y = newY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) showFabMenu(v)
                    true
                }
                else -> false
            }
        }
    }

    private fun showFabMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.apply {
            add(0, R.id.menu_settings, 0, R.string.menu_settings)
            add(0, 100, 1, R.string.menu_reload)
            add(0, 101, 2, R.string.menu_log)
            add(0, 102, 3, R.string.menu_listener)
            add(0, 103, 4, R.string.menu_battery)
            add(0, 104, 5, R.string.menu_packages)
            add(0, 105, 6, R.string.menu_server)
            add(0, 106, 7, R.string.menu_tts)
            add(0, 107, 8, R.string.menu_popup)
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_settings -> startActivity(Intent(this, SettingsActivity::class.java))
                100 -> webView.reload()
                101 -> startActivity(Intent(this, LogActivity::class.java))
                102 -> openListenerSettings()
                103 -> requestBatteryExemption()
                104 -> askPackages()
                105 -> askServerUrl()
                106 -> startActivity(Intent(this, SettingsActivity::class.java))
                107 -> startActivity(Intent(this, SettingsActivity::class.java))
            }
            true
        }
        popup.show()
    }

    // ── Navigasi ──

    private fun loadHome() {
        val b = base; if (b.isBlank()) return
        loadedBase = b; webView.loadUrl("$b/")
    }

    private fun handleIntent(i: Intent?): Boolean {
        val data = i?.data ?: return false
        if (data.scheme != "fr3velora" || data.host != "auth") return false
        val code = data.getQueryParameter("code") ?: return false
        if (!Regex("^[a-f0-9]{48}$").matches(code)) return false
        webView.loadUrl("$base/api/auth/app-session?code=$code")
        return true
    }

    private fun openGoogleLogin() {
        val uri = Uri.parse("$base/api/auth/google?app=1")
        CustomTabsIntent.Builder().build().launchUrl(this, uri)
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            val baseHost = Uri.parse(base).host
            if (uri.host != null && uri.host == baseHost) {
                if (uri.path?.startsWith("/api/auth/google") == true && uri.getQueryParameter("app") != "1") {
                    openGoogleLogin(); return true
                }
                return false
            }
            return try { startActivity(Intent(Intent.ACTION_VIEW, uri)); true }
            catch (_: Exception) { true }
        }

        override fun onPageFinished(view: WebView, url: String?) {
            swipe.isRefreshing = false
            val path = Uri.parse(url ?: "").path ?: ""
            if (path != "/login" && url?.startsWith(base) == true) fetchDeviceKey()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            swipe.isRefreshing = false
            val html = """
                <html><body style="background:#050507;color:#fff;font-family:sans-serif;text-align:center;padding-top:30vh">
                <p>Tidak bisa terhubung ke server.</p>
                <p style="color:#98A2B6;font-size:13px">${base}</p>
                <a href="${base}/" style="color:#FF5C3A">Coba lagi</a></body></html>
            """.trimIndent()
            view.loadDataWithBaseURL(base, html, "text/html", "utf-8", null)
        }
    }

    // ── Device key ──

    private fun fetchDeviceKey() {
        val now = System.currentTimeMillis()
        if (now - lastKeyFetch < 30_000) return
        lastKeyFetch = now
        val b = base
        val cookie = CookieManager.getInstance().getCookie(b) ?: return
        thread {
            try {
                val r = Api.request("GET", b, "/api/profile/device-key", cookie = cookie)
                if (r.code == 200) {
                    val key = JSONObject(r.body).optString("device_key")
                    if (key.isNotBlank() && key != Prefs.deviceKey(this)) {
                        Prefs.setDeviceKey(this, key)
                        Prefs.log(this, "Akun terhubung ke app")
                    }
                    Dispatcher.heartbeat(this); Dispatcher.flushAsync(this)
                    if (key.isNotBlank() && Prefs.welcomedKey(this) != key) {
                        val name = try {
                            val me = Api.request("GET", b, "/api/auth/me", cookie = cookie)
                            if (me.code == 200) JSONObject(me.body).optString("username") else ""
                        } catch (_: Exception) { "" }
                        runOnUiThread { showWelcome(key, name) }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // ── Sambutan ──

    private fun showWelcome(key: String, name: String) {
        if (isFinishing || isDestroyed) return
        Prefs.setWelcomedKey(this, key)
        val sapaan = if (name.isNotBlank()) getString(R.string.welcome_dear, name)
                     else getString(R.string.welcome_dear_generic)
        AlertDialog.Builder(this)
            .setIcon(R.drawable.logo)
            .setTitle(R.string.welcome_title)
            .setMessage(sapaan + "\n\n" + getString(R.string.welcome_body))
            .setPositiveButton(R.string.welcome_start, null)
            .setCancelable(false).show()
        val who = if (name.isNotBlank()) ", $name" else ""
        Announcer.speakOnly(this, getString(R.string.welcome_speech, who))
    }

    // ── Izin notifikasi ──

    private fun listenerEnabled() =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun promptListenerOnce() {
        if (listenerPrompted || listenerEnabled() || isFinishing || isDestroyed) return
        listenerPrompted = true
        AlertDialog.Builder(this)
            .setIcon(R.drawable.logo)
            .setTitle(R.string.listener_title)
            .setMessage(R.string.banner_listener)
            .setPositiveButton(R.string.btn_enable) { _, _ -> openListenerSettings() }
            .setNeutralButton(R.string.btn_app_info) { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")))
            }
            .setNegativeButton(R.string.later, null).show()
    }

    private fun openListenerSettings() {
        try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        catch (_: Exception) {
            Toast.makeText(this, R.string.err_open_settings, Toast.LENGTH_LONG).show()
        }
    }

    private fun maybePromptBattery() {
        if (!listenerEnabled() || Prefs.batteryPromptShown(this)) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        Prefs.setBatteryPromptShown(this)
        AlertDialog.Builder(this)
            .setTitle(R.string.battery_title).setMessage(R.string.battery_msg)
            .setPositiveButton(R.string.allow) { _, _ -> requestBatteryExemption() }
            .setNegativeButton(R.string.later, null).show()
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryExemption() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    // ── Dialog server URL ──

    private fun askServerUrl() {
        val input = EditText(this).apply {
            setText(Prefs.baseUrl(this@MainActivity))
            hint = "https://example.com"
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.server_title).setMessage(R.string.server_msg).setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val url = input.text.toString().trimEnd('/')
                if (!url.startsWith("https://")) {
                    Toast.makeText(this, R.string.err_https, Toast.LENGTH_SHORT).show()
                } else { Prefs.setBaseUrl(this, url); loadHome() }
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    // ── Dialog paket DANA ──

    private fun askPackages() {
        val input = EditText(this).apply { setText(Prefs.packagesRaw(this@MainActivity)) }
        AlertDialog.Builder(this)
            .setTitle(R.string.packages_title).setMessage(R.string.packages_msg).setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                Prefs.setPackagesRaw(this, input.text.toString())
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    // ── Lifecycle ──

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent); handleIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState); webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (base.isNotBlank() && loadedBase.isNotBlank() && base != loadedBase) loadHome()
        promptListenerOnce(); maybePromptBattery()
        Dispatcher.heartbeat(this); Dispatcher.flushAsync(this)
    }

    override fun onPause() { CookieManager.getInstance().flush(); super.onPause() }

    override fun onDestroy() { fileCallback?.onReceiveValue(null); fileCallback = null; super.onDestroy() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    // ── Download ──

    private fun handleDownload(url: String, contentDisposition: String?, mime: String?) {
        val name = URLUtil.guessFileName(url, contentDisposition, mime)
        when {
            url.startsWith("blob:") -> {
                val js = """
                    (async function(){try{
                      var r=await fetch(${JSONObject.quote(url)});var b=await r.blob();
                      var fr=new FileReader();
                      fr.onloadend=function(){Fr3veloraApp.saveBase64(${JSONObject.quote(name)},
                        b.type||${JSONObject.quote(mime ?: "application/octet-stream")},String(fr.result).split(',')[1]);};
                      fr.readAsDataURL(b);
                    }catch(e){}})();
                """.trimIndent()
                webView.evaluateJavascript(js, null)
            }
            url.startsWith("data:") -> {
                val comma = url.indexOf(',')
                if (comma > 0) saveBytes(name, mime ?: "application/octet-stream",
                    Base64.decode(url.substring(comma + 1), Base64.DEFAULT))
            }
            url.startsWith("http") && Uri.parse(url).host == Uri.parse(base).host -> {
                val req = DownloadManager.Request(Uri.parse(url))
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                CookieManager.getInstance().getCookie(url)?.let { req.addRequestHeader("Cookie", it) }
                (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                Toast.makeText(this, getString(R.string.downloading, name), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveBytes(name: String, mime: String, bytes: ByteArray) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("insert gagal")
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            runOnUiThread { Toast.makeText(this, getString(R.string.saved_to_downloads, name), Toast.LENGTH_SHORT).show() }
        } catch (_: Exception) {
            runOnUiThread { Toast.makeText(this, R.string.err_save, Toast.LENGTH_LONG).show() }
        }
    }

    private inner class Bridge {
        @JavascriptInterface fun isApp(): Boolean = true
        @JavascriptInterface fun appVersion(): String = BuildConfig.VERSION_NAME
        @JavascriptInterface fun isListenerEnabled(): Boolean = listenerEnabled()
        @JavascriptInterface fun openNotificationSettings() { runOnUiThread { openListenerSettings() } }
        @JavascriptInterface fun saveBase64(name: String, mime: String, b64: String) {
            if (b64.length > 20_000_000) return
            val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100).ifBlank { "download" }
            saveBytes(safe, mime, Base64.decode(b64, Base64.DEFAULT))
        }
    }
}

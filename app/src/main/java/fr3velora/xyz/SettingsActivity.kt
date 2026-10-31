package fr3velora.xyz

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Pusat Kontrol: status perangkat, sumber notifikasi (khusus DANA Bisnis), pop-up/suara, server, diagnostik.
 * Tampilan mengikuti website (warna, kartu tipis, aksen oranye) dan dibangun lewat kode agar ringan.
 */
class SettingsActivity : AppCompatActivity() {

    private companion object {
        const val BG = 0xFF0A0D13.toInt()
        const val CARD = 0xFF10141D.toInt()
        const val CARD2 = 0xFF161C28.toInt()
        const val LINE = 0xFF212A3B.toInt()
        const val T1 = 0xFFE7EBF3.toInt()
        const val T2 = 0xFF98A2B6.toInt()
        const val T3 = 0xFF5F6B81.toInt()
        const val ACCENT = 0xFFFF5C3A.toInt()
        const val SUCCESS = 0xFF34D17C.toInt()
        const val WARN = 0xFFF5A623.toInt()
        const val DANGER = 0xFFF0564E.toInt()
        val RECOMMENDED = Regex("(?i)(bisnis|business|merchant)")
    }

    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        window.statusBarColor = BG
        window.navigationBarColor = BG
        val scroll = ScrollView(this).apply {
            setBackgroundColor(BG)
            isFillViewport = true
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        scroll.addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    // ── Susunan halaman ──

    private fun build() {
        content.removeAllViews()
        header()
        statusSection()
        sourceSection()
        alertSection()
        serverSection()
        diagnosticSection()
        footer()
    }

    private fun header() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(text("‹", 28f, T1, bold = true).apply {
            setPadding(dp(4), 0, dp(16), dp(4))
            setOnClickListener { finish() }
        })
        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.logo)
            clipToOutline = true
            background = rounded(CARD, dp(12).toFloat(), 0)
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }, LinearLayout.LayoutParams(dp(52), dp(52)))
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        titles.addView(text(getString(R.string.app_name), 20f, T1, bold = true))
        titles.addView(text("Pusat Kontrol · v${BuildConfig.VERSION_NAME}", 12f, T2))
        row.addView(titles)
        content.addView(row, lp(bottom = 20))
    }

    private fun statusSection() {
        section("Status perangkat")
        val card = card()
        val listenerOn = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        statusRow(card, "Akses notifikasi", if (listenerOn) "Pembaca notifikasi berjalan" else "Wajib aktif agar pembayaran terbaca",
            if (listenerOn) "AKTIF" else "MATI", if (listenerOn) SUCCESS else DANGER,
            if (listenerOn) null else "Aktifkan" to {
                try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                catch (_: Exception) { Toast.makeText(this, R.string.err_open_settings, Toast.LENGTH_LONG).show() }
            })
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryOk = pm.isIgnoringBatteryOptimizations(packageName)
        statusRow(card, "Optimasi baterai", "Agar tidak dimatikan sistem di latar belakang",
            if (batteryOk) "BEBAS" else "DIBATASI", if (batteryOk) SUCCESS else WARN,
            if (batteryOk) null else "Atur" to { requestBattery() })
        val popOk = NotificationManagerCompat.from(this).areNotificationsEnabled()
        statusRow(card, "Izin pop-up", "Notifikasi pembayaran masuk",
            if (popOk) "AKTIF" else "MATI", if (popOk) SUCCESS else DANGER,
            if (popOk) null else "Buka" to {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            })
        val linked = Prefs.deviceKey(this) != null
        statusRow(card, "Akun terhubung", if (linked) "Perangkat terdaftar di server" else "Login di halaman utama dulu",
            if (linked) "TERHUBUNG" else "BELUM", if (linked) SUCCESS else WARN, null)
        val queued = Prefs.queueList(this).size
        statusRow(card, "Antrean kirim", "Notifikasi menunggu jaringan",
            "$queued", if (queued == 0) SUCCESS else WARN, null, last = true)
        content.addView(card, lp(bottom = 20))
    }

    private fun sourceSection() {
        section("Sumber notifikasi · DANA Bisnis")
        val card = card()
        card.addView(text(
            "Hanya notifikasi dari aplikasi yang dicentang di bawah yang dibaca. Notifikasi aplikasi lain " +
                "(WhatsApp, bank, game, dll) diabaikan total: tidak dibaca, tidak dikirim, tanpa pop-up.",
            12f, T2
        ).apply { setPadding(dp(14), dp(12), dp(14), dp(10)) })

        val allowed = Prefs.allowedPackages(this)
        val installed = installedDanaApps()
        val shown = LinkedHashMap<String, Pair<String, Boolean>>() // pkg → (label, terpasang)
        installed.forEach { (pkg, label) -> shown[pkg.lowercase()] = label to true }
        allowed.forEach { if (it !in shown) shown[it] = "Paket manual" to false }

        shown.entries.forEachIndexed { i, (pkg, info) ->
            val (label, isInstalled) = info
            val rec = isInstalled && RECOMMENDED.containsMatchIn("$label $pkg")
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(10))
            }
            val cb = CheckBox(this).apply {
                isChecked = pkg in allowed
                buttonTintList = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(ACCENT, T3)
                )
                setOnCheckedChangeListener { btn, on ->
                    val next = Prefs.allowedPackages(this@SettingsActivity).toMutableSet()
                    if (on) next.add(pkg) else next.remove(pkg)
                    if (next.isEmpty()) {
                        btn.setOnCheckedChangeListener(null)
                        btn.isChecked = true
                        Toast.makeText(this@SettingsActivity, "Pilih minimal satu aplikasi sumber", Toast.LENGTH_SHORT).show()
                        build()
                    } else {
                        Prefs.setPackagesRaw(this@SettingsActivity, next.joinToString(","))
                        Prefs.log(this@SettingsActivity, "Sumber notifikasi diubah: ${next.joinToString(", ")}")
                    }
                }
            }
            row.addView(cb)
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6), 0, 0, 0) }
            col.addView(text(label, 14f, T1, bold = true))
            col.addView(text(pkg, 11f, T3, mono = true))
            row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (rec) row.addView(pill("DISARANKAN", ACCENT))
            else if (!isInstalled) row.addView(pill("TIDAK TERPASANG", T3))
            card.addView(divider())
            card.addView(row)
            if (i == shown.size - 1) card.addView(divider())
        }
        if (shown.isEmpty()) card.addView(text("Belum ada aplikasi DANA terdeteksi.", 12f, T3).apply {
            setPadding(dp(14), dp(4), dp(14), dp(12))
        })

        val recommended = installed.filter { RECOMMENDED.containsMatchIn("${it.second} ${it.first}") }.map { it.first.lowercase() }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), dp(4), dp(8), dp(8)) }
        if (recommended.isNotEmpty() && allowed != recommended.toSet()) {
            actions.addView(linkButton("Hanya yang disarankan") {
                Prefs.setPackagesRaw(this, recommended.joinToString(","))
                Prefs.log(this, "Sumber notifikasi dibatasi ke: ${recommended.joinToString(", ")}")
                build()
            })
        }
        actions.addView(linkButton("Tambah paket manual") { askManualPackage() })
        card.addView(actions)
        content.addView(card, lp(bottom = 20))
    }

    private fun alertSection() {
        section("Pop-up & suara")
        val card = card()
        switchRow(card, "Pop-up pembayaran", "Tampilkan notifikasi saat pembayaran baru, lunas, atau kedaluwarsa",
            Prefs.popupEnabled(this), first = true) { Prefs.setPopupEnabled(this, it) }
        switchRow(card, "Suara pembayaran (TTS)", "Bacakan pembayaran dalam Bahasa Indonesia",
            Prefs.ttsEnabled(this)) { Prefs.setTtsEnabled(this, it) }
        content.addView(card, lp(bottom = 20))
    }

    private fun serverSection() {
        section("Server")
        val card = card()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(text("URL website", 14f, T1, bold = true))
        col.addView(text(Prefs.baseUrl(this).ifBlank { "(belum diatur)" }, 12f, T2, mono = true))
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(linkButton("Ubah") { askServer() })
        card.addView(row)
        content.addView(card, lp(bottom = 20))
    }

    private fun diagnosticSection() {
        section("Diagnostik")
        val card = card()
        switchRow(card, "Mode debug", "Catat notifikasi aplikasi lain ke log lokal (tidak pernah dikirim ke server)",
            Prefs.debugCapture(this), first = true) { Prefs.setDebugCapture(this, it) }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setOnClickListener { startActivity(Intent(this@SettingsActivity, LogActivity::class.java)) }
        }
        row.addView(text("Log notifikasi", 14f, T1, bold = true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(text("›", 22f, T3))
        card.addView(divider())
        card.addView(row)
        content.addView(card, lp(bottom = 20))
    }

    private fun footer() {
        content.addView(text("$packageName\nBuild ${BuildConfig.VERSION_CODE}", 11f, T3, mono = true).apply {
            gravity = Gravity.CENTER
        }, lp(top = 4))
    }

    // ── Aksi ──

    @SuppressLint("BatteryLife")
    private fun requestBattery() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun askManualPackage() {
        val input = EditText(this).apply { hint = "contoh: id.dana.business" }
        AlertDialog.Builder(this)
            .setIcon(R.drawable.logo)
            .setTitle("Tambah paket manual")
            .setMessage("Nama paket aplikasi DANA Bisnis. Lihat yang benar lewat Log notifikasi dengan mode debug.")
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val v = input.text.toString().trim().lowercase()
                if (Regex("^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+$").matches(v)) {
                    val next = Prefs.allowedPackages(this) + v
                    Prefs.setPackagesRaw(this, next.joinToString(","))
                    build()
                } else Toast.makeText(this, "Nama paket tidak valid", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun askServer() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setText(Prefs.baseUrl(this@SettingsActivity).ifBlank { "https://" })
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setIcon(R.drawable.logo)
            .setTitle(R.string.server_title)
            .setMessage(R.string.server_msg)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val v = input.text.toString().trim().trimEnd('/')
                if (v.startsWith("https://") && v.length > 10) {
                    Prefs.setBaseUrl(this, v)
                    Prefs.setDeviceKey(this, null) // server baru → ambil ulang setelah login
                    build()
                } else Toast.makeText(this, R.string.err_https, Toast.LENGTH_LONG).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Aplikasi di launcher yang paketnya diawali id.dana atau berlabel "DANA". */
    private fun installedDanaApps(): List<Pair<String, String>> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return try {
            pm.queryIntentActivities(intent, 0)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .filter { (pkg, label) -> pkg.startsWith("id.dana", true) || label.contains("dana", true) }
                .distinctBy { it.first }
                .sortedBy { it.first }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── Komponen tampilan ──

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun lp(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(top); bottomMargin = dp(bottom) }

    private fun rounded(fill: Int, radius: Float, stroke: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius
        if (stroke != 0) setStroke(dp(1), stroke)
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false, mono: Boolean = false) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            typeface = when {
                mono -> Typeface.MONOSPACE
                bold -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
                else -> Typeface.SANS_SERIF
            }
        }

    private fun section(title: String) {
        content.addView(text(title.uppercase(), 11f, T3, bold = true).apply {
            letterSpacing = 0.1f
            setPadding(dp(2), 0, 0, dp(8))
        })
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(CARD, dp(4).toFloat(), LINE)
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(LINE)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
    }

    private fun pill(label: String, color: Int) = TextView(this).apply {
        text = label
        textSize = 10f
        setTextColor(color)
        typeface = Typeface.MONOSPACE
        letterSpacing = 0.06f
        setPadding(dp(8), dp(3), dp(8), dp(3))
        background = rounded((color and 0x00FFFFFF) or 0x1F000000, dp(3).toFloat(), 0)
    }

    private fun linkButton(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 13f
        setTextColor(ACCENT)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(10), dp(8), dp(10), dp(8))
        setOnClickListener { onClick() }
    }

    private fun statusRow(
        parent: LinearLayout, title: String, sub: String, pillText: String, pillColor: Int,
        action: Pair<String, () -> Unit>?, last: Boolean = false
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(10), dp(12))
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(text(title, 14f, T1, bold = true))
        col.addView(text(sub, 12f, T2))
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(pill(pillText, pillColor))
        if (action != null) row.addView(linkButton(action.first, action.second))
        else row.addView(View(this), LinearLayout.LayoutParams(dp(4), 1))
        if (parent.childCount > 0) parent.addView(divider())
        parent.addView(row)
    }

    private fun switchRow(
        parent: LinearLayout, title: String, sub: String, checked: Boolean,
        first: Boolean = false, onChange: (Boolean) -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(text(title, 14f, T1, bold = true))
        col.addView(text(sub, 12f, T2))
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(12) })
        val sw = SwitchCompat(this).apply {
            isChecked = checked
            thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(ACCENT, T2))
            trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(0x66FF5C3A, CARD2))
            setOnCheckedChangeListener { _, on -> onChange(on) }
        }
        row.addView(sw)
        row.setOnClickListener { sw.toggle() }
        if (!first) parent.addView(divider())
        parent.addView(row)
    }
}

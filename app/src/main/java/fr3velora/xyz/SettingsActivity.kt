package fr3velora.xyz

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Pusat Kontrol — redesign sekelas fintech.
 * Palette: bg #080B12, card #0F1420, border #1E2A3A, accent #FF5C3A
 */
class SettingsActivity : AppCompatActivity() {

    private companion object {
        val BG        = Color.parseColor("#080B12")
        val SURFACE   = Color.parseColor("#0F1420")
        val SURFACE2  = Color.parseColor("#141B28")
        val BORDER    = Color.parseColor("#1E2A3A")
        val T1        = Color.parseColor("#EDF0F7")
        val T2        = Color.parseColor("#8A96AD")
        val T3        = Color.parseColor("#4A5568")
        val ACCENT    = Color.parseColor("#FF5C3A")
        val SUCCESS   = Color.parseColor("#22C55E")
        val WARN      = Color.parseColor("#F59E0B")
        val DANGER    = Color.parseColor("#EF4444")
        val ACCENT_DIM = Color.argb(30, 255, 92, 58)
        val SUCCESS_DIM = Color.argb(30, 34, 197, 94)
        val WARN_DIM   = Color.argb(30, 245, 158, 11)
        val DANGER_DIM  = Color.argb(30, 239, 68, 68)
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
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(40))
        }
        scroll.addView(content, lp())
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    // ── Page builder ──

    private fun build() {
        content.removeAllViews()
        buildHeader()
        buildStatus()
        buildSources()
        buildAlerts()
        buildDiagnostics()
        buildFooter()
    }

    // ─── Header ───

    private fun buildHeader() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(20))
        }

        // Back button
        val back = FrameLayout(this).apply {
            background = circle(SURFACE, BORDER)
            setOnClickListener { finish() }
        }
        val backArrow = TextView(this).apply {
            text = "‹"
            textSize = 22f
            setTextColor(T1)
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        back.addView(backArrow, FrameLayout.LayoutParams(dp(36), dp(36)).also { it.gravity = Gravity.CENTER })
        row.addView(back, LinearLayout.LayoutParams(dp(40), dp(40)))

        row.addView(space(12, 0))

        // Logo with gradient ring
        val logoWrap = FrameLayout(this)
        val logoBg = View(this).apply { background = circleGradient() }
        logoWrap.addView(logoBg, FrameLayout.LayoutParams(dp(48), dp(48)))
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = circle(BG)
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }
        logoWrap.addView(logo, FrameLayout.LayoutParams(dp(42), dp(42)).also { it.gravity = Gravity.CENTER })
        row.addView(logoWrap, LinearLayout.LayoutParams(dp(48), dp(48)))

        row.addView(space(12, 0))

        // Title block
        val titleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleCol.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 17f
            setTextColor(T1)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        titleCol.addView(TextView(this).apply {
            text = "Pusat Kontrol  ·  v${BuildConfig.VERSION_NAME}"
            textSize = 11f
            setTextColor(T3)
            letterSpacing = 0.04f
        })
        row.addView(titleCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        content.addView(row, lp())
    }

    // ─── Status Section ───

    private fun buildStatus() {
        sectionLabel("Status Perangkat")
        val card = makeCard()

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val listenerOn = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        val batteryOk  = pm.isIgnoringBatteryOptimizations(packageName)
        val notifOn    = NotificationManagerCompat.from(this).areNotificationsEnabled()
        val linked     = Prefs.deviceKey(this) != null
        val queued     = Prefs.queueList(this).size

        val rows = listOf(
            StatusRow("Akses Notifikasi",  "Wajib aktif untuk membaca pembayaran",
                listenerOn, if (listenerOn) "AKTIF" else "MATI",
                if (listenerOn) null else "Aktifkan" to {
                    try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    catch (_: Exception) { Toast.makeText(this, R.string.err_open_settings, Toast.LENGTH_LONG).show() }
                }),
            StatusRow("Optimasi Baterai",  "Aktif terus di latar belakang",
                batteryOk,  if (batteryOk) "BEBAS" else "DIBATASI",
                if (batteryOk) null else "Atur" to { requestBattery() }),
            StatusRow("Izin Pop-up",       "Notifikasi pembayaran masuk",
                notifOn,    if (notifOn) "AKTIF" else "MATI",
                if (notifOn) null else "Aktifkan" to {
                    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                }),
            StatusRow("Akun",              if (linked) "Perangkat terdaftar di server" else "Login di halaman utama",
                linked,     if (linked) "TERHUBUNG" else "BELUM",  null),
            StatusRow("Antrean Kirim",     "Notifikasi menunggu jaringan",
                queued == 0, queued.toString(), null)
        )

        rows.forEachIndexed { i, r ->
            if (i > 0) card.addView(divider())
            card.addView(statusRow(r))
        }

        content.addView(card, lp(bottom = 20))
    }

    private data class StatusRow(
        val title: String, val sub: String, val ok: Boolean,
        val badge: String, val action: Pair<String, () -> Unit>?
    )

    private fun statusRow(r: StatusRow): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(12), dp(14))
        }

        // Dot indicator
        val dot = View(this).apply {
            background = circle(if (r.ok) SUCCESS else if (r.badge == "DIBATASI" || r.badge == "BELUM") WARN else DANGER)
        }
        row.addView(dot, LinearLayout.LayoutParams(dp(7), dp(7)).also { it.rightMargin = dp(11); it.gravity = Gravity.CENTER_VERTICAL })

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply {
            text = r.title; textSize = 13.5f; setTextColor(T1)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        col.addView(TextView(this).apply { text = r.sub; textSize = 11.5f; setTextColor(T2) })
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        row.addView(space(8, 0))

        // Badge
        val badgeColor = when {
            r.ok && r.badge == r.badge.toIntOrNull()?.toString() -> if (r.badge == "0") SUCCESS else WARN
            r.ok -> SUCCESS
            r.badge == "DIBATASI" || r.badge == "BELUM" -> WARN
            else -> DANGER
        }
        val badgeDim = when (badgeColor) {
            SUCCESS -> SUCCESS_DIM; WARN -> WARN_DIM; else -> DANGER_DIM
        }
        row.addView(badge(r.badge, badgeColor, badgeDim))

        if (r.action != null) {
            row.addView(space(8, 0))
            row.addView(TextView(this).apply {
                text = r.action.first
                textSize = 12f
                setTextColor(ACCENT)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(dp(10), dp(6), dp(10), dp(6))
                background = rounded(ACCENT_DIM, dp(6).toFloat(), 0)
                setOnClickListener { r.action.second.invoke() }
            })
        }
        return row
    }

    // ─── Sources Section ───

    private fun buildSources() {
        sectionLabel("Sumber Notifikasi  ·  DANA Bisnis")
        val card = makeCard()

        card.addView(TextView(this).apply {
            text = "Hanya notifikasi dari aplikasi yang dicentang yang dibaca dan dikirim ke server."
            textSize = 12f
            setTextColor(T2)
            lineSpacingMultiplier = 1.4f
            setPadding(dp(16), dp(12), dp(16), dp(14))
        })
        card.addView(divider())

        val allowed   = Prefs.allowedPackages(this)
        val installed = installedDanaApps()
        val shown     = LinkedHashMap<String, Pair<String, Boolean>>()
        installed.forEach { (pkg, label) -> shown[pkg.lowercase()] = label to true }
        allowed.forEach { if (it !in shown) shown[it] = "Paket manual" to false }

        shown.entries.forEachIndexed { i, (pkg, info) ->
            val (label, isInstalled) = info
            val rec = isInstalled && RECOMMENDED.containsMatchIn("$label $pkg")
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }

            val cb = CheckBox(this).apply {
                isChecked = pkg in allowed
                buttonTintList = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(ACCENT, T3)
                )
                setOnCheckedChangeListener { btn, on ->
                    val next = Prefs.allowedPackages(this@SettingsActivity).toMutableSet()
                    if (on) next.add(pkg) else next.remove(pkg)
                    if (next.isEmpty()) {
                        btn.setOnCheckedChangeListener(null); btn.isChecked = true
                        Toast.makeText(this@SettingsActivity, "Pilih minimal satu aplikasi", Toast.LENGTH_SHORT).show()
                        build()
                    } else {
                        Prefs.setPackagesRaw(this@SettingsActivity, next.joinToString(","))
                    }
                }
            }
            row.addView(cb, LinearLayout.LayoutParams(dp(24), dp(24)).also { it.rightMargin = dp(10); it.gravity = Gravity.CENTER_VERTICAL })

            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(TextView(this).apply {
                text = label; textSize = 13.5f; setTextColor(T1)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            })
            col.addView(TextView(this).apply {
                text = pkg; textSize = 11f; setTextColor(T3)
                typeface = Typeface.MONOSPACE
            })
            row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            when {
                rec          -> row.addView(badge("DISARANKAN", ACCENT, ACCENT_DIM))
                !isInstalled -> row.addView(badge("TIDAK TERPASANG", T3, Color.argb(25, 90, 100, 120)))
            }

            if (i > 0) card.addView(divider())
            card.addView(row)
        }

        if (shown.isEmpty()) {
            card.addView(TextView(this).apply {
                text = "Belum ada aplikasi DANA terdeteksi."
                textSize = 12f; setTextColor(T2)
                setPadding(dp(16), dp(8), dp(16), dp(12))
            })
        }

        card.addView(divider())
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        actions.addView(linkBtn("+ Tambah paket manual") { askManualPackage() })
        card.addView(actions)

        content.addView(card, lp(bottom = 20))
    }

    // ─── Alerts Section ───

    private fun buildAlerts() {
        sectionLabel("Pop-up & Suara")
        val card = makeCard()
        switchRow(card, "Pop-up Pembayaran",
            "Tampilkan notifikasi saat ada pembayaran masuk, lunas, atau kedaluwarsa",
            Prefs.popupEnabled(this), true) { Prefs.setPopupEnabled(this, it) }
        card.addView(divider())
        switchRow(card, "Suara Pembayaran (TTS)",
            "Bacakan jumlah dan referensi dalam Bahasa Indonesia",
            Prefs.ttsEnabled(this)) { Prefs.setTtsEnabled(this, it) }
        content.addView(card, lp(bottom = 20))
    }

    // ─── Diagnostics Section ───

    private fun buildDiagnostics() {
        sectionLabel("Diagnostik")
        val card = makeCard()
        switchRow(card, "Mode Debug",
            "Catat notifikasi semua aplikasi ke log lokal (tidak dikirim ke server)",
            Prefs.debugCapture(this), true) { Prefs.setDebugCapture(this, it) }
        card.addView(divider())
        val navRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener { startActivity(Intent(this@SettingsActivity, LogActivity::class.java)) }
        }
        navRow.addView(TextView(this).apply {
            text = "Log Notifikasi"
            textSize = 13.5f; setTextColor(T1)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        navRow.addView(TextView(this).apply { text = "›"; textSize = 20f; setTextColor(T3) })
        card.addView(navRow)
        content.addView(card, lp(bottom = 20))
    }

    // ─── Footer ───

    private fun buildFooter() {
        content.addView(TextView(this).apply {
            text = "${packageName}\nBuild ${BuildConfig.VERSION_CODE}"
            textSize = 10f; setTextColor(T3)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            lineSpacingMultiplier = 1.5f
        }, lp(top = 4))
    }

    // ─── Helpers: Actions ───

    @SuppressLint("BatteryLife")
    private fun requestBattery() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun askManualPackage() {
        val input = EditText(this).apply {
            hint = "id.dana.business"
            setHintTextColor(T3)
            setTextColor(T1)
            background = rounded(SURFACE2, dp(8).toFloat(), BORDER)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        AlertDialog.Builder(this)
            .setIcon(R.drawable.logo)
            .setTitle("Tambah Paket Manual")
            .setMessage("Nama paket aplikasi DANA Bisnis (contoh: id.dana.business). Cek nama yang benar lewat Log Notifikasi → mode debug.")
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val v = input.text.toString().trim().lowercase()
                if (Regex("^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+$").matches(v)) {
                    Prefs.setPackagesRaw(this, (Prefs.allowedPackages(this) + v).joinToString(","))
                    build()
                } else Toast.makeText(this, "Format paket tidak valid", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun installedDanaApps(): List<Pair<String, String>> {
        val pm = packageManager
        return try {
            pm.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .filter { (pkg, label) -> pkg.startsWith("id.dana", true) || label.contains("dana", true) }
                .distinctBy { it.first }.sortedBy { it.first }
        } catch (_: Exception) { emptyList() }
    }

    // ─── Helpers: Views ───

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun lp(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(top); bottomMargin = dp(bottom) }

    private fun space(w: Int, h: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(w), dp(h))
    }

    private fun rounded(fill: Int, radius: Float, border: Int) = GradientDrawable().apply {
        setColor(fill); cornerRadius = radius
        if (border != 0) setStroke(dp(1), border)
    }

    private fun circle(fill: Int, border: Int = 0) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL; setColor(fill)
        if (border != 0) setStroke(dp(1), border)
    }

    private fun circleGradient(): GradientDrawable {
        val g = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(ACCENT, Color.parseColor("#FF8F73")))
        g.shape = GradientDrawable.OVAL
        return g
    }

    private fun makeCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(SURFACE, dp(14).toFloat(), BORDER)
        clipToOutline = true
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(BORDER)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).also {
            it.leftMargin = dp(16); it.rightMargin = dp(16)
        }
    }

    private fun sectionLabel(text: String) {
        content.addView(TextView(this).apply {
            this.text = text.uppercase()
            textSize = 10.5f
            setTextColor(T3)
            letterSpacing = 0.10f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(4), 0, 0, dp(8))
        }, lp(top = 4, bottom = 0))
    }

    private fun badge(label: String, color: Int, dimColor: Int) = TextView(this).apply {
        text = label
        textSize = 10f
        setTextColor(color)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.06f
        setPadding(dp(8), dp(4), dp(8), dp(4))
        background = rounded(dimColor, dp(5).toFloat(), 0)
    }

    private fun linkBtn(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 13f
        setTextColor(ACCENT)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(8), dp(8), dp(8), dp(8))
        setOnClickListener { onClick() }
    }

    private fun switchRow(
        parent: LinearLayout, title: String, sub: String,
        checked: Boolean, first: Boolean = false, onChange: (Boolean) -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(13), dp(16), dp(13))
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply {
            text = title; textSize = 13.5f; setTextColor(T1)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        col.addView(TextView(this).apply {
            text = sub; textSize = 11.5f; setTextColor(T2)
            lineSpacingMultiplier = 1.3f
        })
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            .also { it.rightMargin = dp(12) })

        val sw = SwitchCompat(this).apply {
            isChecked = checked
            thumbTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(ACCENT, T3)
            )
            trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Color.argb(80, 255, 92, 58), SURFACE2)
            )
            setOnCheckedChangeListener { _, on -> onChange(on) }
        }
        row.addView(sw)
        row.setOnClickListener { sw.toggle() }
        parent.addView(row)
    }
}

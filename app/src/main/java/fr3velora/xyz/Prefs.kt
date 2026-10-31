package fr3velora.xyz

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Penyimpanan lokal: URL server, device key, antrean kirim, dan log diagnostik. */
object Prefs {
    private const val MAX_LOG = 80
    private const val MAX_QUEUE = 300

    private fun sp(c: Context) =
        c.applicationContext.getSharedPreferences("fr3velora", Context.MODE_PRIVATE)

    /** Alamat server tetap (ditanam di build); nilai lama di penyimpanan diabaikan. */
    fun baseUrl(@Suppress("UNUSED_PARAMETER") c: Context): String = BuildConfig.DEFAULT_BASE_URL

    fun deviceKey(c: Context): String? = sp(c).getString("device_key", null)?.takeIf { it.isNotBlank() }

    fun setDeviceKey(c: Context, v: String?) {
        sp(c).edit().putString("device_key", v).apply()
    }

    fun packagesRaw(c: Context): String =
        sp(c).getString("packages", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.DANA_PACKAGES

    fun setPackagesRaw(c: Context, v: String) {
        sp(c).edit().putString("packages", v.trim()).apply()
    }

    fun allowedPackages(c: Context): Set<String> =
        packagesRaw(c).split(',', ';', ' ', '\n').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

    fun debugCapture(c: Context): Boolean = sp(c).getBoolean("debug_capture", false)

    fun setDebugCapture(c: Context, v: Boolean) {
        sp(c).edit().putBoolean("debug_capture", v).apply()
    }

    fun ttsEnabled(c: Context): Boolean = sp(c).getBoolean("tts", true)

    fun setTtsEnabled(c: Context, v: Boolean) {
        sp(c).edit().putBoolean("tts", v).apply()
    }

    fun popupEnabled(c: Context): Boolean = sp(c).getBoolean("popup", true)

    fun setPopupEnabled(c: Context, v: Boolean) {
        sp(c).edit().putBoolean("popup", v).apply()
    }

    /** Device key akun yang sudah disambut; akun baru akan disambut lagi. */
    fun welcomedKey(c: Context): String? = sp(c).getString("welcomed_key", null)

    fun setWelcomedKey(c: Context, v: String) {
        sp(c).edit().putString("welcomed_key", v).apply()
    }

    fun batteryPromptShown(c: Context): Boolean = sp(c).getBoolean("battery_prompt", false)

    fun setBatteryPromptShown(c: Context) {
        sp(c).edit().putBoolean("battery_prompt", true).apply()
    }

    // ── Antrean kirim (supaya notifikasi tidak hilang saat jaringan putus) ──

    @Synchronized
    fun queueList(c: Context): List<JSONObject> {
        val arr = JSONArray(sp(c).getString("queue", "[]"))
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    @Synchronized
    fun queueAdd(c: Context, ev: JSONObject) {
        val list = queueList(c).toMutableList()
        val id = ev.optString("event_id")
        if (list.any { it.optString("event_id") == id }) return
        list.add(ev)
        while (list.size > MAX_QUEUE) list.removeAt(0)
        sp(c).edit().putString("queue", JSONArray(list).toString()).apply()
    }

    @Synchronized
    fun queueRemove(c: Context, eventId: String) {
        val list = queueList(c).filter { it.optString("event_id") != eventId }
        sp(c).edit().putString("queue", JSONArray(list).toString()).apply()
    }

    // ── Log diagnostik (terbaru di atas) ──

    @Synchronized
    fun log(c: Context, msg: String) {
        val old = JSONArray(sp(c).getString("log", "[]"))
        val out = JSONArray()
        val stamp = SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(Date())
        out.put("$stamp  $msg")
        for (i in 0 until minOf(old.length(), MAX_LOG - 1)) out.put(old.getString(i))
        sp(c).edit().putString("log", out.toString()).apply()
    }

    @Synchronized
    fun logLines(c: Context): List<String> {
        val arr = JSONArray(sp(c).getString("log", "[]"))
        return (0 until arr.length()).map { arr.getString(it) }
    }

    @Synchronized
    fun clearLog(c: Context) {
        sp(c).edit().putString("log", "[]").apply()
    }
}

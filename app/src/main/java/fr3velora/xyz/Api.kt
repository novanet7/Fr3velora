package fr3velora.xyz

import android.content.Context
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

class ApiResult(val code: Int, val body: String)

object Api {
    fun request(
        method: String,
        base: String,
        path: String,
        deviceKey: String? = null,
        cookie: String? = null,
        json: JSONObject? = null,
    ): ApiResult {
        val conn = URL(base + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/json")
            deviceKey?.let { conn.setRequestProperty("X-Device-Key", it) }
            cookie?.let { conn.setRequestProperty("Cookie", it) }
            if (json != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return ApiResult(code, text)
        } finally {
            conn.disconnect()
        }
    }
}

/** Mengirim notifikasi uang masuk (dengan antrean/retry) dan heartbeat status perangkat ke server. */
object Dispatcher {
    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000
    private val sender = Executors.newSingleThreadExecutor()
    private val beat = Executors.newSingleThreadExecutor()

    fun iso(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))

    fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    fun submit(ctx: Context, event: JSONObject) {
        val app = ctx.applicationContext
        event.put("queued_at", System.currentTimeMillis())
        Prefs.queueAdd(app, event)
        flushAsync(app)
    }

    fun flushAsync(ctx: Context) {
        val app = ctx.applicationContext
        sender.execute { flush(app) }
    }

    private fun flush(app: Context) {
        val key = Prefs.deviceKey(app)
        val base = Prefs.baseUrl(app)
        if (base.isBlank()) return
        val pending = Prefs.queueList(app)
        if (pending.isEmpty()) return
        if (key == null) {
            Prefs.log(app, "Menunggu login: ${pending.size} notifikasi tertahan (buka app & login)")
            return
        }
        val now = System.currentTimeMillis()
        for (ev in pending) {
            val id = ev.optString("event_id")
            val amount = ev.optLong("amount")
            if (now - ev.optLong("queued_at", now) > MAX_AGE_MS) {
                Prefs.queueRemove(app, id)
                Prefs.log(app, "Dibuang (lebih dari 24 jam): Rp$amount")
                continue
            }
            val body = JSONObject(ev.toString()).apply { remove("queued_at") }
            try {
                val r = Api.request("POST", base, "/api/device/notification", deviceKey = key, json = body)
                when {
                    r.code == 200 -> {
                        Prefs.queueRemove(app, id)
                        val res = runCatching { JSONObject(r.body) }.getOrNull()
                        val msg = when {
                            res?.optBoolean("matched") == true -> "LUNAS payment ${res?.optString("payment_id")}"
                            res?.optBoolean("duplicate") == true -> "duplikat, dilewati"
                            else -> "tidak ada payment pending dengan nominal ini"
                        }
                        Prefs.log(app, "Terkirim Rp$amount → $msg")
                        if (res?.optBoolean("matched") == true) {
                            // Cadangan kalau stream event sedang putus; key sama dengan pesan server → tidak dobel
                            val pid = res?.optString("payment_id") ?: ""
                            val rp = "Rp" + java.text.NumberFormat.getInstance(Locale("id", "ID")).format(amount)
                            Announcer.announce(
                                app, "$pid:transaction.updated:paid", "✅ Pembayaran berhasil", rp,
                                "Pembayaran sebesar $rp telah berhasil diverifikasi."
                            )
                        }
                    }
                    r.code == 401 -> {
                        Prefs.log(app, "Device key ditolak server. Buka app dan login ulang.")
                        return
                    }
                    r.code in 400..499 && r.code != 429 -> {
                        Prefs.queueRemove(app, id)
                        Prefs.log(app, "Ditolak server (${r.code}): ${r.body.take(120)}")
                    }
                    else -> {
                        Prefs.log(app, "Server error ${r.code}, dicoba lagi nanti")
                        return
                    }
                }
            } catch (e: IOException) {
                Prefs.log(app, "Jaringan gagal, dicoba lagi nanti (${e.javaClass.simpleName})")
                return
            }
        }
    }

    fun heartbeat(ctx: Context) {
        val app = ctx.applicationContext
        beat.execute {
            val key = Prefs.deviceKey(app) ?: return@execute
            val base = Prefs.baseUrl(app)
            if (base.isBlank()) return@execute
            try {
                val body = JSONObject()
                    .put("device_name", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
                    .put("ip_address", localIp(app))
                    .put("battery_percent", battery(app))
                    .put("app_version", BuildConfig.VERSION_NAME)
                    .put("fcm_registered", false)
                Api.request("POST", base, "/api/device/status", deviceKey = key, json = body)
            } catch (_: Exception) {
            }
        }
    }

    private fun battery(app: Context): Int = try {
        val bm = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
    } catch (_: Exception) {
        0
    }

    private fun localIp(app: Context): String = try {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val lp = cm.getLinkProperties(cm.activeNetwork)
        lp?.linkAddresses?.map { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress ?: "-"
    } catch (_: Exception) {
        "-"
    }
}

package fr3velora.xyz

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Menjaga koneksi SSE ke /api/device/events selama pembaca notifikasi aktif.
 * Server mengirim pesan yang sama seperti push FCM (judul, isi, teks TTS).
 */
class EventStream(private val ctx: Context) {
    @Volatile private var running = false
    private var thread: Thread? = null
    @Volatile private var conn: HttpURLConnection? = null

    @Synchronized
    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "fr3-events").also { it.isDaemon = true; it.start() }
    }

    @Synchronized
    fun stop() {
        running = false
        try { conn?.disconnect() } catch (_: Exception) {}
        thread?.interrupt()
        thread = null
    }

    private fun loop() {
        var backoff = 2_000L
        while (running) {
            val key = Prefs.deviceKey(ctx)
            val base = Prefs.baseUrl(ctx)
            if (key == null || base.isBlank()) {
                sleep(5_000)
                continue
            }
            try {
                val c = URL("$base/api/device/events").openConnection() as HttpURLConnection
                conn = c
                c.setRequestProperty("X-Device-Key", key)
                c.setRequestProperty("Accept", "text/event-stream")
                c.connectTimeout = 15_000
                c.readTimeout = 60_000 // server kirim heartbeat tiap 15 detik
                if (c.responseCode != 200) {
                    c.disconnect()
                    sleep(backoff)
                    backoff = minOf(backoff * 2, 60_000L)
                    continue
                }
                backoff = 2_000L
                c.inputStream.bufferedReader().use { r ->
                    while (running) {
                        val line = r.readLine() ?: break
                        if (line.startsWith("data:")) handle(line.removePrefix("data:").trim())
                    }
                }
                c.disconnect()
            } catch (_: InterruptedException) {
                return
            } catch (_: Exception) {
                // putus/timeout → sambung lagi
            }
            if (running) {
                sleep(backoff)
                backoff = minOf(backoff * 2, 60_000L)
            }
        }
    }

    private fun handle(json: String) {
        try {
            val o = JSONObject(json)
            Announcer.announce(
                ctx, o.optString("key"), o.optString("title"), o.optString("body"), o.optString("tts_text")
            )
        } catch (_: Exception) {
        }
    }

    private fun sleep(ms: Long) {
        try { Thread.sleep(ms) } catch (_: InterruptedException) {}
    }
}

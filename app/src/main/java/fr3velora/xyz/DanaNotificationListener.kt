package fr3velora.xyz

import android.app.Notification
import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject

/**
 * Membaca pop-up notifikasi dari aplikasi DANA Bisnis, mengambil nominal uang masuk,
 * lalu mengirimnya ke server (/api/device/notification) untuk mencocokkan transaksi pending.
 */
class DanaNotificationListener : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private var events: EventStream? = null
    private val tick = object : Runnable {
        override fun run() {
            Dispatcher.heartbeat(applicationContext)
            Dispatcher.flushAsync(applicationContext) // coba kirim ulang yang tertahan
            handler.postDelayed(this, HEARTBEAT_MS)
        }
    }

    override fun onListenerConnected() {
        Prefs.log(this, "Pembaca notifikasi aktif")
        handler.removeCallbacks(tick)
        handler.post(tick)
        Announcer.ensureChannel(this)
        events?.stop()
        events = EventStream(applicationContext).also { it.start() }
    }

    override fun onListenerDisconnected() {
        handler.removeCallbacks(tick)
        events?.stop()
        events = null
        Prefs.log(this, "Pembaca notifikasi terputus, mencoba tersambung lagi")
        try {
            requestRebind(ComponentName(this, DanaNotificationListener::class.java))
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        events?.stop()
        events = null
        Announcer.shutdown()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        try {
            handle(sbn)
        } catch (e: Exception) {
            Prefs.log(this, "Error membaca notifikasi: ${e.message}")
        }
    }

    private fun handle(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg == packageName) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        // Aplikasi selain DANA Bisnis (WhatsApp, bank, game, dll): langsung dibuang, tidak dibaca isinya,
        // tidak dikirim ke server, dan tidak memunculkan pop-up. Isi hanya dicatat jika mode debug aktif.
        val isDana = pkg.lowercase() in Prefs.allowedPackages(this)
        if (!isDana) {
            if (Prefs.debugCapture(this)) Prefs.log(this, "[debug] $pkg → ${collectText(n).take(160)}")
            return
        }
        // Notifikasi "sedang berjalan" (ongoing) dari DANA bukan notifikasi uang masuk
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        val text = collectText(n)

        val result = NotificationParser.parse(text)
        val amount = result.amount
        if (amount == null) {
            Prefs.log(this, "Diabaikan (${result.note}): ${text.take(120)}")
            return
        }
        Prefs.log(this, "Uang masuk terbaca Rp$amount ($pkg)")

        val event = JSONObject()
            .put("amount", amount)
            .put("raw_text", text.take(500))
            .put("posted_at", Dispatcher.iso(sbn.postTime))
            .put("received_at", Dispatcher.iso(System.currentTimeMillis()))
            .put("notification_key", sbn.key)
            .put("event_id", Dispatcher.sha256("$pkg|${sbn.key}|${sbn.postTime}|$text").take(64))
        Dispatcher.submit(this, event)
    }

    private fun collectText(n: Notification): String {
        val ex = n.extras
        val parts = LinkedHashSet<String>()
        fun add(cs: CharSequence?) {
            cs?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
        }
        add(ex.getCharSequence(Notification.EXTRA_TITLE))
        add(ex.getCharSequence(Notification.EXTRA_TEXT))
        add(ex.getCharSequence(Notification.EXTRA_BIG_TEXT))
        add(ex.getCharSequence(Notification.EXTRA_SUB_TEXT))
        ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { add(it) }
        add(n.tickerText)
        return parts.joinToString(" | ")
    }

    companion object {
        private const val HEARTBEAT_MS = 45_000L // server menganggap offline setelah 90 detik
    }
}

package fr3velora.xyz

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Locale

/** Pop-up (heads-up notification) + suara TTS lewat jalur suara aksesibilitas. */
object Announcer {
    private const val CHANNEL = "payments"
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val pendingSpeech = ArrayDeque<String>()
    private val recentKeys = ArrayDeque<String>()

    @Synchronized
    private fun seen(key: String): Boolean {
        if (recentKeys.contains(key)) return true
        recentKeys.addLast(key)
        while (recentKeys.size > 100) recentKeys.removeFirst()
        return false
    }

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, ctx.getString(R.string.channel_payments), NotificationManager.IMPORTANCE_HIGH)
        )
    }

    /** key dipakai untuk mencegah pengumuman ganda untuk kejadian yang sama. */
    fun announce(ctx: Context, key: String, title: String, body: String, speech: String) {
        if (seen(key)) return
        val app = ctx.applicationContext
        if (Prefs.popupEnabled(app)) showPopup(app, key, title, body)
        if (Prefs.ttsEnabled(app)) speak(app, speech)
    }

    /** Hanya suara (tanpa pop-up), mengikuti pengaturan TTS. */
    fun speakOnly(ctx: Context, text: String) {
        val app = ctx.applicationContext
        if (Prefs.ttsEnabled(app)) speak(app, text)
    }

    private fun showPopup(app: Context, key: String, title: String, body: String) {
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return
        ensureChannel(app)
        val open = PendingIntent.getActivity(
            app, 0, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setLargeIcon(BitmapFactory.decodeResource(app.resources, R.drawable.logo))
            .setColor(0xFF1E90FF.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(app).notify(key.hashCode(), n)
        } catch (_: SecurityException) {
        }
    }

    @Synchronized
    private fun speak(app: Context, text: String) {
        if (text.isBlank()) return
        if (tts == null) {
            tts = TextToSpeech(app) { status ->
                synchronized(this) {
                    if (status == TextToSpeech.SUCCESS) {
                        val t = tts ?: return@synchronized
                        val r = t.setLanguage(Locale("id", "ID"))
                        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                            t.setLanguage(Locale.getDefault())
                        }
                        // Jalur "suara aksesibilitas" agar tetap terdengar dan volumenya terpisah dari media
                        t.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        ttsReady = true
                        while (pendingSpeech.isNotEmpty()) say(pendingSpeech.removeFirst())
                    } else {
                        Prefs.log(app, "TTS gagal diinisialisasi (status $status)")
                    }
                }
            }
        }
        if (ttsReady) say(text) else pendingSpeech.addLast(text)
    }

    private fun say(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "fr3-${System.nanoTime()}")
    }

    @Synchronized
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        pendingSpeech.clear()
    }
}

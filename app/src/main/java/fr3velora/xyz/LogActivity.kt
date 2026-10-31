package fr3velora.xyz

import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Log diagnostik: apa yang terbaca dari DANA, hasil parsing, dan hasil kirim ke server. */
class LogActivity : AppCompatActivity() {
    private lateinit var logText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)
        logText = findViewById(R.id.logText)
        val debugSwitch = findViewById<Switch>(R.id.switchDebug)
        debugSwitch.isChecked = Prefs.debugCapture(this)
        debugSwitch.setOnCheckedChangeListener { _, on -> Prefs.setDebugCapture(this, on) }
        findViewById<Button>(R.id.btnRefreshLog).setOnClickListener { render() }
        findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            Prefs.clearLog(this)
            render()
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val status = buildString {
            append("Server: ").append(Prefs.baseUrl(this@LogActivity).ifBlank { "(belum diatur)" }).append('\n')
            append("Akun terhubung: ").append(if (Prefs.deviceKey(this@LogActivity) != null) "ya" else "belum (login dulu)").append('\n')
            append("Paket dibaca: ").append(Prefs.packagesRaw(this@LogActivity)).append('\n')
            append("Antrean tertahan: ").append(Prefs.queueList(this@LogActivity).size).append("\n\n")
        }
        val lines = Prefs.logLines(this)
        logText.text = status + if (lines.isEmpty()) getString(R.string.log_empty) else lines.joinToString("\n")
    }
}

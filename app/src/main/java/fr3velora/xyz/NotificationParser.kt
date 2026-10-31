package fr3velora.xyz

/**
 * Mengambil nominal uang masuk dari teks notifikasi DANA Bisnis.
 * Murni Kotlin (tanpa API Android) supaya bisa diuji dengan JUnit.
 *
 * Aturan: teks harus memuat kata "uang masuk" (menerima/diterima/masuk/pembayaran...) dan tidak memuat
 * kata gagal/batal/refund; nominal "Rp..." yang didahului kata saldo/cashback/promo/dll dilewati.
 * Server tetap hanya melunasi transaksi pending dengan nominal unik yang persis sama,
 * jadi salah tangkap tidak akan melunasi transaksi lain.
 */
object NotificationParser {
    data class Result(val amount: Long?, val note: String)

    private val RP = Regex("""(?i)(?<![A-Za-z])(?:rp\.?|idr)\s*(\d[\d.,]*)""")
    private val POSITIVE = Regex(
        """(?i)(masuk|menerima|diterima|terima|dibayar|pembayaran|payment|received|transfer|top ?up|setor)"""
    )
    private val NEGATIVE_ALL = Regex(
        """(?i)(gagal|dibatalkan|batal|kedaluwarsa|kadaluarsa|expired|failed|keluar|penarikan|ditarik|refund|pengembalian)"""
    )
    private val NEGATIVE_NEAR = Regex(
        """(?i)(saldo|cashback|promo|diskon|voucher|kupon|limit|biaya|fee|admin|poin|bonus|potongan|hemat|minimal|maksimal|min\.|maks\.)"""
    )

    fun parse(text: String): Result {
        if (text.isBlank()) return Result(null, "teks kosong")
        if (NEGATIVE_ALL.containsMatchIn(text)) return Result(null, "mengandung kata gagal/keluar/refund")
        if (!POSITIVE.containsMatchIn(text)) return Result(null, "bukan notifikasi uang masuk")
        for (m in RP.findAll(text)) {
            val before = text.substring(maxOf(0, m.range.first - 28), m.range.first)
            if (NEGATIVE_NEAR.containsMatchIn(before)) continue
            val amount = toAmount(m.groupValues[1]) ?: continue
            if (amount > 0) return Result(amount, "ok")
        }
        return Result(null, "nominal tidak ditemukan")
    }

    /** "10.123" -> 10123, "10.123,00" -> 10123, "1,000" -> 1000, "500" -> 500. */
    internal fun toAmount(raw: String): Long? {
        var s = raw.trimEnd('.', ',')
        if (s.isEmpty()) return null
        val lastSep = maxOf(s.lastIndexOf('.'), s.lastIndexOf(','))
        if (lastSep >= 0) {
            val after = s.length - lastSep - 1
            if (after in 1..2) s = s.substring(0, lastSep) // desimal, dibuang
        }
        return s.filter { it.isDigit() }.toLongOrNull()
    }
}

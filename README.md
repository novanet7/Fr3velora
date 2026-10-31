# Fr3velora App (WebView + pembaca notifikasi DANA Bisnis)

Satu APK: memuat website payment gateway di WebView **dan** membaca notifikasi DANA Bisnis,
jadi tidak perlu APK listener terpisah.

## Cara kerja
1. App membuka website kamu di WebView. Login Google dibuka lewat Chrome Custom Tab (Google memblokir
   OAuth di WebView), lalu kembali ke app lewat `fr3velora://auth?code=...`.
2. Setelah login, app mengambil `device_key` dari `/api/profile/device-key` (memakai sesi WebView).
3. `DanaNotificationListener` membaca notifikasi paket DANA, mengambil nominal uang masuk,
   dan mengirim ke `POST /api/device/notification` (header `X-Device-Key`).
4. Server mencocokkan nominal unik dengan transaksi pending -> lunas -> webhook ke merchant.
5. Heartbeat tiap 45 detik ke `/api/device/status` (kartu "Status APK" di beranda).

Notifikasi yang gagal terkirim disimpan di antrean lokal dan dicoba lagi (maks 24 jam).

## Build (GitHub Actions)
1. Push folder ini ke repo GitHub.
2. Settings -> Secrets and variables -> Actions -> Variables: `BASE_URL` = `https://domain-kamu` (opsional, bisa diisi di app).
3. (Disarankan) Secrets: `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` agar APK bisa di-update tanpa uninstall.
4. Actions -> Build APK -> Run workflow -> unduh artifact `fr3velora-apk`.

Lokal: `gradle assembleRelease -PBASE_URL=https://domain-kamu` (JDK 17 + Android SDK).

## Setelah instal
- Login di app, lalu aktifkan **Akses notifikasi** untuk Fr3velora (banner di atas akan memandu).
- Android 13+: jika pilihan abu-abu -> Info aplikasi -> ⋮ -> *Izinkan setelan terbatas*.
- Izinkan abaikan optimasi baterai (dan "autostart"/"tanpa batasan" di HP Xiaomi/Oppo/Vivo/Samsung).
- Cek **Menu -> Log notifikasi**. Nyalakan *mode debug*, kirim pembayaran tes, dan lihat nama paket
  aplikasi DANA yang benar; kalau beda dari bawaan, ubah di **Menu -> Paket aplikasi DANA**.

## Pop-up & suara
- App menjaga koneksi stream ke `/api/device/events` (tanpa FCM) selama akses notifikasi aktif.
- Pop-up (heads-up) + suara TTS Bahasa Indonesia untuk: permintaan pembayaran baru, pembayaran lunas, kedaluwarsa.
- Suara diputar lewat jalur **suara aksesibilitas** (USAGE_ASSISTANCE_ACCESSIBILITY).
- Bisa dimatikan di menu: *Suara pembayaran (TTS)* dan *Pop-up pembayaran*.
- Butuh mesin Text-to-Speech (Google TTS) dengan data bahasa Indonesia terpasang.

## Pusat Kontrol (menu ⋮ → Pusat Kontrol)
Halaman bawaan app dengan tema yang sama seperti website:
- **Status perangkat**: akses notifikasi, optimasi baterai, izin pop-up, akun terhubung, antrean kirim.
- **Sumber notifikasi · DANA Bisnis**: daftar aplikasi DANA yang terpasang (otomatis terdeteksi); hanya yang dicentang yang dibaca.
  Aplikasi lain diabaikan total (tidak dibaca, tidak dikirim, tanpa pop-up). Tombol "Hanya yang disarankan" memilih aplikasi Bisnis/Merchant saja.
- **Pop-up & suara**, **Server** (URL website), dan **Diagnostik** (mode debug + log).

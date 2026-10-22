# iOS Launcher untuk Android (min. Android 7.0 / API 24)

Launcher bergaya iOS dengan bahasa visual Liquid Glass: ikon squircle, dock kaca, status bar iOS (jam, sinyal, Wi-Fi, baterai dengan persen),
Pusat Notifikasi, pencarian aplikasi, dan layar kunci.

## Cara build
1. Buka folder ini di Android Studio (Hedgehog / Iguana atau lebih baru, JDK 17), tunggu Gradle sync.
2. Build > Build APK(s), atau jalankan langsung ke ponsel (aktifkan USB debugging).
3. Buka aplikasi **Pengaturan Launcher** (ikon kotak biru) lalu:
   - ketuk **Jadikan launcher utama**, pilih iOS Launcher;
   - aktifkan **Akses notifikasi** (agar notifikasi tampil);
   - aktifkan **Tampil di atas aplikasi lain**, lalu nyalakan **Layar kunci gaya iOS**.
4. Android 13+ untuk APK yang dipasang manual: jika akses notifikasi abu-abu, buka Info Aplikasi > menu titik tiga > **Izinkan setelan terbatas**.
5. Matikan optimasi baterai untuk aplikasi ini agar layar kunci tidak dimatikan sistem (terutama Xiaomi, Oppo, Vivo, Samsung).

## Gestur
- Tarik status bar ke bawah: Pusat Notifikasi (ketuk pegangan di bawah untuk menutup).
- Tarik layar utama ke bawah: pencarian aplikasi.
- Tekan lama ikon: Info aplikasi / Hapus aplikasi.
- Layar kunci: geser ke atas untuk membuka; tombol senter dan kamera di sudut bawah.

## Batasan yang perlu diketahui
- Android tidak mengizinkan aplikasi biasa mengganti keyguard sistem. Layar kunci di sini tampil di atasnya; PIN/pola/sidik jari tetap diminta sistem.
- Status bar asli disembunyikan dan digantikan tampilan sendiri. Gestur tarik dari tepi paling atas layar dapat tetap memunculkan panel notifikasi bawaan Android.
- Efek kaca memakai transparansi + garis tepi, bukan blur sungguhan.
- Ikon aplikasi sistem umum (telepon, pesan, kamera, dll.) digambar ulang bergaya iOS; ikon aplikasi lain dipotong ke bentuk squircle.
- Belum ada: Control Center, widget, mode edit/geser ikon, folder.

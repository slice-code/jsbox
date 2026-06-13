<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# JS-Sandbox (JSBox) - Android Runtime & Web IDE

JS-Sandbox adalah platform runtime andal dan lingkungan pengembangan (IDE) terintegrasi pada perangkat Android untuk membangun, menguji, dan mengeksekusi aplikasi JavaScript berbasis DOM dengan bridge native Android.

---

## 📱 Google Play Store / App Store Listing Description

### Short Description (Deskripsi Singkat)
> **ID:** Bangun dan jalankan aplikasi JavaScript DOM langsung di HP Anda dengan akses penuh ke API native Android seperti getaran, alarm, dan background service.
> 
> **EN:** Build and execute JavaScript DOM applications directly on your phone with native Android API access for vibration, alarms, and background services.

### Long Description (Deskripsi Lengkap)
**JS-Sandbox** mengubah perangkat Android Anda menjadi laboratorium pemrograman JavaScript portabel. Tulis kode JavaScript DOM standar, jalankan di dalam kontainer sandbox WebView berkecepatan tinggi, dan berinteraksilah langsung dengan sistem operasi Android menggunakan bridge native berkinerja tinggi.

---

## ⚡ Fitur Utama (Key Features)

*   **🗂️ Dashboard Workspace Proyek**: Kelola banyak proyek kode dengan mudah. Sesuaikan deskripsi proyek, nama, dan ikon proyek secara kustom (mendukung pengunggahan gambar langsung dari galeri).
*   **💻 Remote Web IDE (Local Server)**: Jalankan server lokal langsung di HP Anda pada port `5009` (akses Wi-Fi). Edit file kode proyek Anda secara nirkabel dengan nyaman menggunakan keyboard/layar PC Anda lewat browser web!
*   **🔄 Instant Run (Hot-Reloading)**: Kirim perubahan kode secara nirkabel dari Web IDE PC ke HP Anda secara instan menggunakan sinkronisasi WebSocket tanpa perlu memulai ulang aplikasi.
*   **✏️ Editor Kode Sora Terintegrasi**: Tulis kode langsung di HP Anda menggunakan editor kode native Android kelas atas dengan fitur auto-save otomatis, line numbers, dan pemformatan yang ramah pengembang.
*   **🔗 Native Android Bridge APIs**: Akses modul perangkat keras native Android secara langsung dari skrip JavaScript Anda:
    *   `showToast(msg)` — Memunculkan pesan toast native.
    *   `vibrate(ms)` — Mengaktifkan getaran HP dengan durasi kustom.
    *   `createAlarm(label, hour, minute)` — Menjadwalkan alarm sistem native.
    *   `startBackgroundService(filename)` — Menjalankan skrip background secara persisten di latar belakang.
*   **⚙️ Headless Background Service**: Jalankan skrip headless (seperti `service.js`) secara persisten di latar belakang menggunakan Android Foreground Service.
*   **🐞 Live Console Log Debugger**: Panel debug konsol bawaan di bagian bawah layar pratinjau runtime untuk melacak pesan `console.log()` dan error runtime JS secara langsung.

---

## 🚀 Cara Menjalankan Secara Lokal (Run Locally)

### Prasyarat
*   **Android Studio** (Koala atau versi yang lebih baru)
*   **JDK 17 / 21**

### Langkah-Langkah:
1.  Buka Android Studio.
2.  Pilih **Open** dan arahkan ke direktori proyek ini.
3.  Biarkan Android Studio melakukan sinkronisasi Gradle (menggunakan Gradle `9.3.1`).
4.  Pastikan Anda telah mengisi berkas konfigurasi [.env](file:///home/gugus/Documents/Project/jsbox/.env) lokal Anda.
5.  Jalankan aplikasi pada Emulator atau Perangkat Fisik Android Anda.

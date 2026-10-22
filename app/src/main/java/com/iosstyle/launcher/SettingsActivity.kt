package com.iosstyle.launcher

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat

/** Pengaturan sederhana: izin yang dibutuhkan dan opsi tampilan. Dibuka dari ikon "Pengaturan Launcher". */
class SettingsActivity : Activity() {

    override fun onResume() {
        super.onResume()
        setContentView(build())
    }

    private fun build(): View {
        val black = 0xFF000000.toInt()
        val gray = 0xFF8E8E93.toInt()
        val sv = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(48f), dp(16f), dp(32f))
        }
        sv.addView(col)
        col.addView(tv(this, "Pengaturan", 34f, black, true))

        col.addView(section("Dasar"))
        col.addView(card(
            row("Jadikan launcher utama", "Pilih iOS Launcher sebagai aplikasi Layar Utama") { pickHome() },
            row(
                "Akses notifikasi",
                if (notifOn()) "Aktif" else "Belum aktif. Ketuk untuk mengaktifkan"
            ) { startSafe(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            row(
                "Tampil di atas aplikasi lain",
                if (Settings.canDrawOverlays(this)) "Aktif" else "Belum aktif. Dibutuhkan untuk layar kunci"
            ) {
                startSafe(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        ))

        col.addView(section("Tampilan"))
        col.addView(card(
            switchRow("Layar kunci gaya iOS", Prefs.lockEnabled(this)) { on -> toggleLock(on) },
            switchRow("Wallpaper bawaan", Prefs.builtinWallpaper(this)) { Prefs.setBuiltinWallpaper(this, it) },
            switchRow("Jam ringkas di layar kunci", Prefs.compactClock(this)) { Prefs.setCompactClock(this, it) }
        ))

        val note = tv(
            this,
            "Layar kunci ini tampil di atas layar kunci Android. Jika ponsel memakai PIN, pola, atau sidik jari, " +
                "sistem tetap memintanya setelah Anda menggeser ke atas.",
            13f, gray, false
        )
        note.maxLines = 6
        col.addView(note, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12f) })
        return sv
    }

    private fun toggleLock(on: Boolean) {
        if (on) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Aktifkan dulu \"Tampil di atas aplikasi lain\"", Toast.LENGTH_LONG).show()
                startSafe(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                Prefs.setLockEnabled(this, false)
                return
            }
            Prefs.setLockEnabled(this, true)
            LockService.start(this)
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
            }
        } else {
            Prefs.setLockEnabled(this, false)
            LockService.stop(this)
        }
    }

    private fun notifOn() = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun pickHome() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            startSafe(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun startSafe(i: Intent) {
        try {
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "Pengaturan ini tidak tersedia di perangkat Anda", Toast.LENGTH_SHORT).show()
        }
    }

    private fun section(t: String): View =
        tv(this, t.uppercase(), 13f, 0xFF8E8E93.toInt(), false).apply {
            setPadding(dp(16f), dp(24f), 0, dp(6f))
        }

    private fun row(title: String, sub: String?, onClick: () -> Unit): View {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            setOnClickListener { onClick() }
        }
        r.addView(tv(this, title, 17f, 0xFF000000.toInt(), false))
        if (sub != null) r.addView(tv(this, sub, 13f, 0xFF8E8E93.toInt(), false))
        return r
    }

    private fun switchRow(title: String, on: Boolean, change: (Boolean) -> Unit): View {
        val r = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
        }
        r.addView(tv(this, title, 17f, 0xFF000000.toInt(), false), LinearLayout.LayoutParams(0, WRAP, 1f))
        val sw = Switch(this)
        sw.isChecked = on
        sw.setOnCheckedChangeListener { _, v -> change(v) }
        r.addView(sw)
        return r
    }

    private fun card(vararg rows: View): View {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(14f).toFloat()
            }
        }
        rows.forEachIndexed { i, v ->
            if (i > 0) {
                val line = View(this)
                line.setBackgroundColor(0xFFE5E5EA.toInt())
                c.addView(line, LinearLayout.LayoutParams(MATCH, 1).apply { leftMargin = dp(16f) })
            }
            c.addView(v)
        }
        return c
    }
}

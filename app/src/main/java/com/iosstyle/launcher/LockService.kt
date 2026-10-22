package com.iosstyle.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder

/**
 * Menyalakan layar kunci kustom: saat layar mati, LockActivity disiapkan di atas keyguard sistem.
 * Android 10+ hanya mengizinkan start activity dari latar belakang jika izin "Tampil di atas aplikasi lain" aktif.
 */
class LockService : Service() {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (!Prefs.lockEnabled(c)) return
            try {
                c.startActivity(
                    Intent(c, LockActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                )
            } catch (e: Exception) { }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotification())
        registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (e: Exception) { }
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(): Notification {
        val b = if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("lock", "Layar kunci", NotificationManager.IMPORTANCE_MIN))
            Notification.Builder(this, "lock")
        } else {
            Notification.Builder(this).setPriority(Notification.PRIORITY_MIN)
        }
        return b.setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Layar kunci iOS aktif")
            .build()
    }

    companion object {
        fun start(c: Context) {
            try {
                val i = Intent(c, LockService::class.java)
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
            } catch (e: Exception) { }
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, LockService::class.java))
        }
    }
}

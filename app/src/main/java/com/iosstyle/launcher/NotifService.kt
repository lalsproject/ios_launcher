package com.iosstyle.launcher

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Membaca notifikasi aktif untuk Pusat Notifikasi dan layar kunci. Perlu izin "Akses notifikasi". */
class NotifService : NotificationListenerService() {
    override fun onListenerConnected() { instance = this; changed() }
    override fun onListenerDisconnected() { if (instance === this) instance = null; changed() }
    override fun onNotificationPosted(sbn: StatusBarNotification) { changed() }
    override fun onNotificationRemoved(sbn: StatusBarNotification) { changed() }

    private fun changed() { for (l in listeners.toList()) l() }

    companion object {
        var instance: NotifService? = null
        val listeners = HashSet<() -> Unit>()

        fun current(): List<StatusBarNotification> = try {
            (instance?.activeNotifications ?: emptyArray<StatusBarNotification>())
                .filter { !it.isOngoing && (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
                .sortedByDescending { it.postTime }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

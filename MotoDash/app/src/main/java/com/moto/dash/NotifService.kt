package com.moto.dash

import android.app.Notification
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotifItem(
    val key: String,
    val app: String,
    val title: String,
    val text: String,
    val time: Long,
    val icon: Drawable?
)

object NotifStore {
    @Volatile
    var items: List<NotifItem> = emptyList()
    var onChange: (() -> Unit)? = null
    var service: NotifService? = null

    fun dismiss(key: String) {
        try {
            service?.cancelNotification(key)
        } catch (e: Exception) {
        }
    }
}

/**
 * Lit les notifications du téléphone. Le même accès sert aussi à lire
 * la session multimédia (Deezer, Spotify, YouTube Music...).
 */
class NotifService : NotificationListenerService() {

    override fun onListenerConnected() {
        NotifStore.service = this
        rebuild()
    }

    override fun onListenerDisconnected() {
        NotifStore.service = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        rebuild()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        rebuild()
    }

    private fun rebuild() {
        val active: Array<StatusBarNotification>? = try {
            activeNotifications
        } catch (e: Exception) {
            null
        }
        if (active == null) return

        val pm = packageManager
        val list = ArrayList<NotifItem>()
        for (sbn in active.sortedByDescending { it.postTime }) {
            val n = sbn.notification
            if (sbn.packageName == packageName) continue
            if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) continue
            if ((n.flags and Notification.FLAG_ONGOING_EVENT) != 0) continue

            val ex = n.extras
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val body = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
            if (title.isEmpty() && body.isEmpty()) continue

            var app = sbn.packageName
            var icon: Drawable? = null
            try {
                val ai = pm.getApplicationInfo(sbn.packageName, 0)
                app = pm.getApplicationLabel(ai).toString()
                icon = pm.getApplicationIcon(ai)
            } catch (e: Exception) {
                try {
                    icon = n.smallIcon?.loadDrawable(this)
                } catch (e2: Exception) {
                }
            }

            val text = if (title.isNotEmpty() && body.isNotEmpty()) "$title : $body" else title + body
            list.add(NotifItem(sbn.key, app, title, text, sbn.postTime, icon))
            if (list.size >= 40) break
        }

        Handler(Looper.getMainLooper()).post {
            NotifStore.items = list
            NotifStore.onChange?.invoke()
        }
    }
}

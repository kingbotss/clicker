package com.personal.tools.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.personal.tools.R

/** Builds the control notification and its Start/Stop + Hide actions. */
object ControlNotification {

    const val CHANNEL_ID = "clicker_controls"
    const val NOTIF_ID = 42

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.notif_channel),
                        NotificationManager.IMPORTANCE_LOW,
                    )
                )
            }
        }
    }

    fun build(context: Context, running: Boolean): Notification {
        ensureChannel(context)
        val statusText = context.getString(
            if (running) R.string.notif_running else R.string.notif_stopped
        )
        val toggleLabel = context.getString(if (running) R.string.stop else R.string.start)

        return Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .addAction(action(context, toggleLabel, ControlReceiver.ACTION_TOGGLE, 1))
            .addAction(
                action(context, context.getString(R.string.hide_overlay), ControlReceiver.ACTION_HIDE, 2)
            )
            .build()
    }

    private fun action(context: Context, label: String, action: String, code: Int): Notification.Action {
        val intent = Intent(context, ControlReceiver::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(context, code, intent, flags)
        return Notification.Action.Builder(null, label, pi).build()
    }
}

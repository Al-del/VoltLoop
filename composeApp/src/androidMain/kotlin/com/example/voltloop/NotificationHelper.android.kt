package com.example.voltloop

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

private const val CHANNEL_ID   = "voltloop_messages"
private const val CHANNEL_NAME = "Messages"

actual fun showLocalNotification(title: String, body: String) {
    val ctx = AppContext.context

    // Create the notification channel once (no-op on re-runs, required on Android 8+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "VoltLoop message notifications"
        }
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_email)
        .setContentTitle(title)
        .setContentText(body)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()

    val notifId = System.currentTimeMillis().toInt()
    try {
        NotificationManagerCompat.from(ctx).notify(notifId, notification)
    } catch (e: SecurityException) {
        // Permission not yet granted — silent fail
        println("Notification permission missing: ${e.message}")
    }
}

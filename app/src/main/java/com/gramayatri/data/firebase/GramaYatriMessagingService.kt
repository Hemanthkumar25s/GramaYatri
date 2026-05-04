package com.gramayatri.data.firebase

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.gramayatri.GramaYatriApplication
import com.gramayatri.MainActivity

class GramaYatriMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Grama-Yatri Alert"

        val body = message.notification?.body
            ?: message.data["body"]
            ?: return

        val type = message.data["type"] ?: "GENERAL"
        val channelId = if (type == "CANCELLED" || type == "EXTRA")
            GramaYatriApplication.CHANNEL_ALERTS
        else
            GramaYatriApplication.CHANNEL_PINGS

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val emoji = when (type) {
            "CANCELLED" -> "❌"
            "DELAY" -> "⏱️"
            "EXTRA" -> "➕"
            "BUS_AT_STOP" -> "🚌"
            else -> "📢"
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$emoji $title")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(
                if (channelId == GramaYatriApplication.CHANNEL_ALERTS)
                    NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Token refresh — in production, save to user's Firestore document
        // for targeted notifications
    }
}

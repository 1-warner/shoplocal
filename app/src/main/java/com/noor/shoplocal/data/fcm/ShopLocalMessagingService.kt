package com.noor.shoplocal.data.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.noor.shoplocal.R
import com.noor.shoplocal.ui.SplashActivity

/**
 * Receives Firebase Cloud Messaging pushes and shows them as system notifications
 * — the app's real-time notification feature (order updates, price-drop alerts,
 * promotions). FCM delivers messages even when the app is in the background.
 */
class ShopLocalMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "shoplocal_updates"
        private const val TAG = "FCM"

        /** Creates the notification channel (safe to call repeatedly). */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = context.getString(R.string.notif_channel_desc) }
                context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
            }
        }
    }

    override fun onNewToken(token: String) {
        // In production this token would be sent to the server to target this device.
        Log.i(TAG, "New FCM token: ${token.take(16)}…")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: getString(R.string.app_name)
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        Log.i(TAG, "Push received: $title")
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        ensureChannel(this)
        val intent = Intent(this, SplashActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pending = PendingIntent.getActivity(this, 0, intent, flags)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        getSystemService(NotificationManager::class.java)
            ?.notify(System.currentTimeMillis().toInt(), notification)
    }
}

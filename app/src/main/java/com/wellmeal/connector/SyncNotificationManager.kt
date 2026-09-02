package com.wellmeal.connector

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class SyncNotificationManager(
    private val context: Context
) {

    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createNotificationChannel()
    }

    /**
     * Creates the notification channel for sync status on Android 8.0+ (API 26+).
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sync status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows quiet notifications for automatic health data synchronization"
                enableVibration(false)
                setSound(null, null)
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * Checks if notification permission is granted on the device.
     */
    fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Shows an ongoing, low-priority progress notification for automatic sync.
     */
    fun showProgressNotification() {
        if (!isNotificationPermissionGranted()) return

        try {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Wellmeal Connector")
                .setContentText("Syncing health data...")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setTimeoutAfter(TIMEOUT_MS)
                .build()

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        } catch (_: SecurityException) {
            // Notification failures must never fail synchronization
        } catch (_: Exception) {
            // Notification failures must never fail synchronization
        }
    }

    /**
     * Cancels any active sync progress notification.
     */
    fun cancelProgressNotification() {
        try {
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {
            // Safe cleanup
        }
    }

    /**
     * Replaces progress notification with a dismissible failure notification.
     */
    fun showFailureNotification() {
        cancelProgressNotification()

        if (!isNotificationPermissionGranted()) return

        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Wellmeal Connector")
                .setContentText("Automatic sync failed")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("Automatic sync failed. Open Wellmeal Connector to review the sync status.")
                )
                .setOngoing(false)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                notificationManager.notify(NOTIFICATION_ID_FAILURE, notification)
            }
        } catch (_: SecurityException) {
            // Notification failures must never fail synchronization
        } catch (_: Exception) {
            // Notification failures must never fail synchronization
        }
    }

    companion object {
        const val CHANNEL_ID = "wellmeal_sync"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_ID_FAILURE = 1002
        private const val TIMEOUT_MS = 10 * 60 * 1000L // 10 minutes
    }
}

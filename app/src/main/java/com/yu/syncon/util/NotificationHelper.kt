package com.yu.syncon.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.yu.syncon.R
import com.yu.syncon.ui.MainActivity

object NotificationHelper {

    const val CHANNEL_TRACKING_ID = "syncon_tracking_service"
    const val CHANNEL_WARNING_ID = "syncon_limit_warnings"
    const val TRACKING_NOTIFICATION_ID = 1001
    const val LIVE_REMAINING_NOTIFICATION_ID = 1002

    fun createNotificationChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        // Low-importance channel for the persistent tracking service
        val trackingChannel = NotificationChannel(
            CHANNEL_TRACKING_ID,
            "Usage Tracking Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Maintains ongoing background SyncOn tracking"
            setShowBadge(false)
        }
        manager.createNotificationChannel(trackingChannel)

        // High-importance heads-up channel for 5-minute warnings
        val warningChannel = NotificationChannel(
            CHANNEL_WARNING_ID,
            "Approaching Limit Warnings",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Fires when you have 5 minutes remaining on an app"
        }
        manager.createNotificationChannel(warningChannel)
    }

    fun buildTrackingServiceNotification(context: Context): Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_TRACKING_ID)
            .setSmallIcon(R.drawable.ic_stat_syncon)
            .setContentTitle("SyncOn Active")
            .setContentText("Monitoring screen time and limits locally")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun showWarningNotification(context: Context, appName: String, remainingMinutes: Int) {
        if (!PermissionUtils.hasNotificationPermission(context)) return

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            appName.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_WARNING_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("$remainingMinutes minutes left")
            .setContentText("You have $remainingMinutes minutes left on $appName today.")
            .setSubText("SyncOn")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(appName.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission revoked mid-operation
        }
    }

    fun updateLiveRemainingNotification(context: Context, appName: String, remainingMinutes: Int) {
        if (!PermissionUtils.hasNotificationPermission(context)) return

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            LIVE_REMAINING_NOTIFICATION_ID,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val text = if (remainingMinutes == 1) {
            "1 minute left today"
        } else {
            "$remainingMinutes minutes left today"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_TRACKING_ID)
            .setSmallIcon(R.drawable.ic_stat_syncon)
            .setContentTitle(appName)
            .setContentText(text)
            .setSubText("SyncOn")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()

        try {
            Log.i("NotificationHelper", "updateLiveRemainingNotification: app=$appName, remaining=$remainingMinutes mins")
            NotificationManagerCompat.from(context).notify(LIVE_REMAINING_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked mid-operation
        }
    }

    fun cancelLiveRemainingNotification(context: Context) {
        try {
            Log.i("NotificationHelper", "cancelLiveRemainingNotification")
            NotificationManagerCompat.from(context).cancel(LIVE_REMAINING_NOTIFICATION_ID)
        } catch (_: Exception) {}
    }
}

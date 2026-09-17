package com.yu.syncon.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.yu.syncon.service.tracking.ForegroundTrackingService
import com.yu.syncon.util.PermissionUtils

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            // Only attempt to start tracking service if core permissions are granted
            if (PermissionUtils.hasUsageAccess(context)) {
                val serviceIntent = Intent(context, ForegroundTrackingService::class.java)
                try {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } catch (_: Exception) {
                    // Safe guard against background execution limits
                }
            }
        }
    }
}

package com.yu.syncon.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.yu.syncon.service.tracking.ForegroundTrackingService
import com.yu.syncon.util.PermissionUtils
import com.yu.syncon.util.DiagnosticLog

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            // Only attempt to start tracking service if core permissions are granted
            if (PermissionUtils.hasUsageAccess(context)) {
                val serviceIntent = Intent(context, ForegroundTrackingService::class.java)
                try {
                    ContextCompat.startForegroundService(context, serviceIntent)
                    DiagnosticLog.record(context, "boot_tracking_start_requested")
                } catch (e: Exception) {
                    DiagnosticLog.record(context, "boot_tracking_start_failed", e.message)
                }
            }
        }
    }
}

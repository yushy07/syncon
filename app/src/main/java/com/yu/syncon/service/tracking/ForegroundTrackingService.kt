package com.yu.syncon.service.tracking

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.yu.syncon.SyncOnApp
import com.yu.syncon.data.repository.UsageRepository
import com.yu.syncon.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ForegroundTrackingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var repository: UsageRepository
    private var lastCheckTime: Long = 0L

    private var isTrackingStarted = false

    companion object {
        private const val TAG = "ForegroundTracking"
    }

    override fun onCreate() {
        super.onCreate()
        repository = (applicationContext as? SyncOnApp)?.repository ?: UsageRepository(applicationContext)
        NotificationHelper.createNotificationChannels(this)
        lastCheckTime = System.currentTimeMillis() - (5 * 60 * 1000L)

        val notification = NotificationHelper.buildTrackingServiceNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NotificationHelper.TRACKING_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(
                    NotificationHelper.TRACKING_NOTIFICATION_ID,
                    notification
                )
            }
        } else {
            startForeground(NotificationHelper.TRACKING_NOTIFICATION_ID, notification)
        }
        Log.i(TAG, "ForegroundTrackingService created and started in foreground")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isTrackingStarted) {
            isTrackingStarted = true
            serviceScope.launch {
                try {
                    // Reconcile any gap that occurred before the service was started
                    repository.reconcileGaps()
                    Log.d(TAG, "Initial gap reconciliation completed")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed during initial gap reconciliation", e)
                }

                // 5-minute periodic tracking loop
                while (isActive) {
                    val now = System.currentTimeMillis()
                    try {
                        repository.processUsageEvents(lastCheckTime, now)
                        lastCheckTime = now
                        Log.d(TAG, "Successfully processed usage tick up to $now")

                        // PRD §7.1: runLimitCheckForCurrentForegroundApp()
                        val currentFg = repository.currentForegroundPackage
                        if (currentFg != null) {
                            val limitCheck = repository.checkAppLimit(currentFg)
                            if (limitCheck != null) {
                                if (limitCheck.shouldWarn) {
                                    NotificationHelper.showWarningNotification(
                                        applicationContext,
                                        limitCheck.appName,
                                        limitCheck.remainingMinutes
                                    )
                                    repository.markWarningShown(currentFg)
                                }
                                if (limitCheck.shouldBlock) {
                                    repository.markAppBlocked(currentFg)
                                    val blockIntent = Intent(applicationContext, com.yu.syncon.ui.blocked.BlockedActivity::class.java).apply {
                                        setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                        putExtra(com.yu.syncon.ui.blocked.BlockedActivity.EXTRA_PACKAGE_NAME, currentFg)
                                        putExtra(com.yu.syncon.ui.blocked.BlockedActivity.EXTRA_APP_NAME, limitCheck.appName)
                                        putExtra(com.yu.syncon.ui.blocked.BlockedActivity.EXTRA_BLOCKING_STYLE, limitCheck.blockingStyle)
                                        putExtra(com.yu.syncon.ui.blocked.BlockedActivity.EXTRA_SNOOZE_MINUTES, limitCheck.snoozeMinutes)
                                        putExtra(com.yu.syncon.ui.blocked.BlockedActivity.EXTRA_LIMIT_MINUTES, limitCheck.limitMinutes)
                                        putExtra(com.yu.syncon.ui.blocked.BlockedActivity.EXTRA_USED_MINUTES, limitCheck.usedMinutes)
                                    }
                                    startActivity(blockIntent)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to process usage events for window ($lastCheckTime, $now)", e)
                    }
                    delay(5 * 60 * 1000L) // 5 minutes
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}

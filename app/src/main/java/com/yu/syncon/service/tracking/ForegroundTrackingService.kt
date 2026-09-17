package com.yu.syncon.service.tracking

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
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

    override fun onCreate() {
        super.onCreate()
        repository = UsageRepository(applicationContext)
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isTrackingStarted) {
            isTrackingStarted = true
            serviceScope.launch {
                // Reconcile any gap that occurred before the service was started
                repository.reconcileGaps()

                // 5-minute periodic tracking loop
                while (isActive) {
                    val now = System.currentTimeMillis()
                    try {
                        repository.processUsageEvents(lastCheckTime, now)
                        lastCheckTime = now
                    } catch (_: Exception) {
                        // Fail-safe to ensure tracking coroutine doesn't terminate
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

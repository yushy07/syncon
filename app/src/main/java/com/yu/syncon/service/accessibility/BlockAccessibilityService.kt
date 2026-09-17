package com.yu.syncon.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.yu.syncon.data.repository.UsageRepository
import com.yu.syncon.ui.blocked.BlockedActivity
import com.yu.syncon.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BlockAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var repository: UsageRepository

    override fun onCreate() {
        super.onCreate()
        repository = UsageRepository(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val targetPackage = event.packageName?.toString() ?: return
        // Never block our own app or common system UI elements
        if (targetPackage == packageName || targetPackage == "com.android.systemui") return

        serviceScope.launch {
            try {
                val result = repository.checkAppLimit(targetPackage) ?: return@launch

                if (result.shouldWarn) {
                    NotificationHelper.showWarningNotification(
                        applicationContext,
                        result.appName,
                        result.remainingMinutes
                    )
                    repository.markWarningShown(targetPackage)
                }

                if (result.shouldBlock) {
                    repository.markAppBlocked(targetPackage)

                    val intent = Intent(applicationContext, BlockedActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(BlockedActivity.EXTRA_PACKAGE_NAME, targetPackage)
                        putExtra(BlockedActivity.EXTRA_APP_NAME, result.appName)
                        putExtra(BlockedActivity.EXTRA_BLOCKING_STYLE, result.blockingStyle)
                        putExtra(BlockedActivity.EXTRA_SNOOZE_MINUTES, result.snoozeMinutes)
                        putExtra(BlockedActivity.EXTRA_LIMIT_MINUTES, result.limitMinutes)
                        putExtra(BlockedActivity.EXTRA_USED_MINUTES, result.usedMinutes)
                    }
                    startActivity(intent)
                }
            } catch (_: Exception) {
                // Ignore unexpected inspection exceptions
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}

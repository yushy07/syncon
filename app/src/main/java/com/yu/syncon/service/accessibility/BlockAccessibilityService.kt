package com.yu.syncon.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.yu.syncon.SyncOnApp
import com.yu.syncon.data.repository.UsageRepository
import com.yu.syncon.ui.blocked.BlockedActivity
import com.yu.syncon.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BlockAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var repository: UsageRepository

    companion object {
        private const val TAG = "BlockAccessibility"
    }

    override fun onCreate() {
        super.onCreate()
        repository = (applicationContext as? SyncOnApp)?.repository ?: UsageRepository(applicationContext)
        Log.i(TAG, "BlockAccessibilityService created")
    }

    private var liveCountdownJob: kotlinx.coroutines.Job? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val targetPackage = event.packageName?.toString() ?: return
        // Never block our own app or common system UI elements
        if (targetPackage == packageName || targetPackage == "com.android.systemui") {
            liveCountdownJob?.cancel()
            repository.setCurrentForegroundApp(null)
            return
        }

        liveCountdownJob?.cancel()
        repository.setCurrentForegroundApp(targetPackage)

        liveCountdownJob = serviceScope.launch {
            try {
                // Immediate check on entry
                val initialResult = repository.checkAppLimit(targetPackage) ?: return@launch

                if (initialResult.shouldBlock) {
                    enforceBlock(targetPackage, initialResult)
                    return@launch
                }

                if (initialResult.shouldWarn) {
                    NotificationHelper.showWarningNotification(
                        applicationContext,
                        initialResult.appName,
                        initialResult.remainingMinutes
                    )
                    repository.markWarningShown(targetPackage)
                }

                // Live countdown loop while user stays in this foreground app
                while (isActive) {
                    delay(10 * 1000L) // check every 10 seconds
                    val check = repository.checkAppLimit(targetPackage) ?: break

                    if (check.shouldWarn) {
                        NotificationHelper.showWarningNotification(
                            applicationContext,
                            check.appName,
                            check.remainingMinutes
                        )
                        repository.markWarningShown(targetPackage)
                    }

                    if (check.shouldBlock) {
                        enforceBlock(targetPackage, check)
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking limit for package: $targetPackage", e)
            }
        }
    }

    private suspend fun enforceBlock(targetPackage: String, result: UsageRepository.LimitCheckResult) {
        Log.w(TAG, "Enforcing block for $targetPackage (Style: ${result.blockingStyle})")
        repository.markAppBlocked(targetPackage)

        val intent = Intent(applicationContext, BlockedActivity::class.java).apply {
            setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockedActivity.EXTRA_PACKAGE_NAME, targetPackage)
            putExtra(BlockedActivity.EXTRA_APP_NAME, result.appName)
            putExtra(BlockedActivity.EXTRA_BLOCKING_STYLE, result.blockingStyle)
            putExtra(BlockedActivity.EXTRA_SNOOZE_MINUTES, result.snoozeMinutes)
            putExtra(BlockedActivity.EXTRA_LIMIT_MINUTES, result.limitMinutes)
            putExtra(BlockedActivity.EXTRA_USED_MINUTES, result.usedMinutes)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        liveCountdownJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}

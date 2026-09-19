package com.yu.syncon.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.yu.syncon.SyncOnApp
import com.yu.syncon.data.repository.UsageRepository
import com.yu.syncon.ui.blocked.BlockedActivity
import com.yu.syncon.util.NotificationHelper
import com.yu.syncon.util.UsageDayCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BlockAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var repository: UsageRepository
    private var powerManager: PowerManager? = null
    private var screenReceiver: BroadcastReceiver? = null

    private var currentForegroundPackage: String? = null
    private var currentForegroundSessionStartMs: Long = 0L
    private var activeCheckJob: Job? = null

    companion object {
        private const val TAG = "BlockAccessibility"
        private const val CHECK_INTERVAL_MS = 20_000L // 20 seconds
        private const val WARNING_THRESHOLD_MINUTES = 5
    }

    override fun onCreate() {
        super.onCreate()
        repository = (applicationContext as? SyncOnApp)?.repository ?: UsageRepository(applicationContext)
        powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        Log.i(TAG, "BlockAccessibilityService created")

        // Register dynamic screen off / on receiver to pause check loop when device is locked/off
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        Log.d(TAG, "Screen off: pausing active check loop and canceling live notification")
                        activeCheckJob?.cancel()
                        activeCheckJob = null
                        // The repository is shared with ForegroundTrackingService. Clearing its
                        // foreground session here prevents the service's five-minute safety check
                        // from treating locked-screen time as live app usage.
                        repository.setCurrentForegroundApp(null)
                        NotificationHelper.cancelLiveRemainingNotification(applicationContext)
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        // The display can be interactive while the lock screen is still visible.
                        // Wait for USER_PRESENT before resuming app-time enforcement.
                        Log.d(TAG, "Screen on: waiting for unlock before resuming checks")
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        Log.d(TAG, "Device unlocked: resetting foreground baseline session start")
                        val pkg = currentForegroundPackage
                        if (pkg != null && pkg != packageName && pkg != "com.android.systemui") {
                            currentForegroundSessionStartMs = System.currentTimeMillis()
                            // Establish a new live baseline instead of continuing the session from
                            // before the device was locked.
                            repository.setCurrentForegroundApp(pkg)
                            startCheckLoop(pkg)
                        }
                    }
                }
            }
        }
        try {
            registerReceiver(screenReceiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register screenReceiver", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val newPackage = event.packageName?.toString() ?: return

        // Never block our own app or common system UI elements (e.g. status bar, notification shade)
        if (newPackage == packageName || newPackage == "com.android.systemui") {
            return
        }

        if (newPackage == currentForegroundPackage) {
            // Duplicate/spurious event for the same app — ignore, do NOT reset the session timer
            return
        }

        // Switching to a genuinely different foreground app (or launcher)
        activeCheckJob?.cancel()
        activeCheckJob = null
        NotificationHelper.cancelLiveRemainingNotification(applicationContext)

        currentForegroundPackage = newPackage
        currentForegroundSessionStartMs = System.currentTimeMillis()
        repository.setCurrentForegroundApp(newPackage)

        startCheckLoop(newPackage)
    }

    private fun startCheckLoop(pkg: String) {
        activeCheckJob?.cancel()
        activeCheckJob = serviceScope.launch {
            val limitCheck = repository.checkAppLimit(pkg)
            if (limitCheck == null) {
                // No limit configured for this app or its category — do NOT start loop
                Log.d(TAG, "No limit configured for $pkg; check loop not started")
                return@launch
            }

            // Run initial check immediately
            runLimitCheck(pkg, currentForegroundSessionStartMs)

            while (isActive && currentForegroundPackage == pkg) {
                delay(CHECK_INTERVAL_MS)
                runLimitCheck(pkg, currentForegroundSessionStartMs)
            }
        }
    }

    private suspend fun runLimitCheck(targetPackage: String, sessionStartMs: Long) {
        // Guard: if screen is currently turned off or non-interactive, do not accumulate time
        if (powerManager?.isInteractive == false) {
            return
        }

        val limitCheck = repository.checkAppLimit(targetPackage) ?: return
        val today = UsageDayCalculator.getTodayUsageDate()
        val dailyState = repository.getOrCreateDailyState(targetPackage, today)

        val remaining = limitCheck.remainingMinutes
        val appName = limitCheck.appName

        Log.i(TAG, "LimitCheck tick for $targetPackage: used=${limitCheck.usedMinutes}, limit=${limitCheck.limitMinutes}, remaining=$remaining")

        // Warning notification check
        if (limitCheck.shouldWarn && !dailyState.warningShown) {
            NotificationHelper.showWarningNotification(
                applicationContext,
                appName,
                remaining
            )
            repository.markWarningShown(targetPackage)
        }

        // Blocking check guarded by !dailyState.isBlocked to prevent duplicate launches
        if (limitCheck.shouldBlock && !dailyState.isBlocked) {
            Log.w(TAG, "Enforcing block for $targetPackage (Used: ${limitCheck.usedMinutes}, Limit: ${limitCheck.limitMinutes}, Style: ${limitCheck.blockingStyle})")
            repository.markAppBlocked(targetPackage)
            NotificationHelper.cancelLiveRemainingNotification(applicationContext)
            launchBlockedActivity(
                packageName = targetPackage,
                appName = appName,
                blockingStyle = limitCheck.blockingStyle,
                snoozeMinutes = limitCheck.snoozeMinutes,
                limitMinutes = limitCheck.limitMinutes,
                usedMinutes = limitCheck.usedMinutes
            )
        } else if (!dailyState.isBlocked) {
            // Live ongoing remaining time notification
            NotificationHelper.updateLiveRemainingNotification(
                applicationContext,
                appName,
                remaining.coerceAtLeast(0)
            )
        }
    }

    private fun launchBlockedActivity(
        packageName: String,
        appName: String,
        blockingStyle: String,
        snoozeMinutes: Int,
        limitMinutes: Int,
        usedMinutes: Int
    ) {
        // Send user to home first so the blocked application is cleanly backgrounded
        performGlobalAction(GLOBAL_ACTION_HOME)

        val intent = Intent(applicationContext, BlockedActivity::class.java).apply {
            setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(BlockedActivity.EXTRA_PACKAGE_NAME, packageName)
            putExtra(BlockedActivity.EXTRA_APP_NAME, appName)
            putExtra(BlockedActivity.EXTRA_BLOCKING_STYLE, blockingStyle)
            putExtra(BlockedActivity.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
            putExtra(BlockedActivity.EXTRA_LIMIT_MINUTES, limitMinutes)
            putExtra(BlockedActivity.EXTRA_USED_MINUTES, usedMinutes)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        activeCheckJob?.cancel()
        activeCheckJob = null
        NotificationHelper.cancelLiveRemainingNotification(applicationContext)
    }

    override fun onDestroy() {
        super.onDestroy()
        activeCheckJob?.cancel()
        activeCheckJob = null
        NotificationHelper.cancelLiveRemainingNotification(applicationContext)
        try {
            screenReceiver?.let { unregisterReceiver(it) }
        } catch (_: Exception) {}
        serviceScope.cancel()
    }
}

package com.yu.syncon.data.repository

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.yu.syncon.data.local.AppDatabase
import com.yu.syncon.data.local.dao.AppConfigDao
import com.yu.syncon.data.local.dao.AppDailyStateDao
import com.yu.syncon.data.local.dao.AppInfoDao
import com.yu.syncon.data.local.dao.AppLimitSettingsDao
import com.yu.syncon.data.local.dao.BlockEventDao
import com.yu.syncon.data.local.dao.DailyUsageDao
import com.yu.syncon.data.local.entity.AppConfig
import com.yu.syncon.data.local.entity.AppDailyState
import com.yu.syncon.data.local.entity.AppInfo
import com.yu.syncon.data.local.entity.AppLimitSettings
import com.yu.syncon.data.local.entity.BlockEvent
import com.yu.syncon.data.local.entity.DailyUsage
import com.yu.syncon.util.CategoryMapper
import com.yu.syncon.util.UsageDayCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class UsageRepository(
    private val context: Context? = null,
    private val database: AppDatabase? = context?.let { AppDatabase.getInstance(it) },
    private val appInfoDao: AppInfoDao = database?.appInfoDao() ?: error("appInfoDao required"),
    private val dailyUsageDao: DailyUsageDao = database?.dailyUsageDao() ?: error("dailyUsageDao required"),
    private val appLimitSettingsDao: AppLimitSettingsDao = database?.appLimitSettingsDao() ?: error("appLimitSettingsDao required"),
    private val appDailyStateDao: AppDailyStateDao = database?.appDailyStateDao() ?: error("appDailyStateDao required"),
    private val blockEventDao: BlockEventDao = database?.blockEventDao() ?: error("blockEventDao required"),
    private val appConfigDao: AppConfigDao = database?.appConfigDao() ?: error("appConfigDao required")
) {

    private val usageStatsManager: UsageStatsManager? by lazy {
        context?.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    }

    // ---------------------------------------------------------
    // Installed Apps Sync
    // ---------------------------------------------------------

    suspend fun syncInstalledApps() = withContext(Dispatchers.IO) {
        val packageManager = context?.packageManager ?: return@withContext
        val installedApps = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledApplications(0)
            }
        } catch (_: Exception) {
            emptyList()
        }

        val existingApps = appInfoDao.getAllStatic().associateBy { it.packageName }
        val newAppInfos = mutableListOf<AppInfo>()

        for (app in installedApps) {
            val pkgName = app.packageName
            val existing = existingApps[pkgName]
            if (existing == null) {
                val appName = try {
                    packageManager.getApplicationLabel(app).toString()
                } catch (_: Exception) {
                    pkgName
                }
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val defaultCategory = CategoryMapper.getDefaultCategory(pkgName)

                newAppInfos.add(
                    AppInfo(
                        packageName = pkgName,
                        appName = appName,
                        category = defaultCategory,
                        isCategoryManuallySet = false,
                        isSystemApp = isSystem
                    )
                )
            }
        }

        if (newAppInfos.isNotEmpty()) {
            appInfoDao.insertOrIgnoreAll(newAppInfos)
        }
    }

    fun getAllAppsFlow(): Flow<List<AppInfo>> = appInfoDao.getAllFlow()

    suspend fun getAppInfo(packageName: String): AppInfo? = withContext(Dispatchers.IO) {
        appInfoDao.getApp(packageName)
    }

    suspend fun updateAppCategory(packageName: String, newCategory: String) = withContext(Dispatchers.IO) {
        appInfoDao.updateCategory(packageName, newCategory, isManuallySet = true)
    }

    // ---------------------------------------------------------
    // ---------------------------------------------------------
    // In-memory Session Continuity & Live Tracking
    // ---------------------------------------------------------
    private val activeOpenSessions = mutableMapOf<String, Long>()
    private val uncommittedMillis = mutableMapOf<Pair<String, String>, Long>()
    
    @Volatile
    var currentForegroundPackage: String? = null
        private set
    @Volatile
    var currentForegroundSessionStartMs: Long = 0L
        private set

    fun setCurrentForegroundApp(packageName: String?) {
        val now = System.currentTimeMillis()
        if (currentForegroundPackage != packageName) {
            currentForegroundPackage = packageName
            currentForegroundSessionStartMs = now
        }
    }

    // ---------------------------------------------------------
    // Usage Tracking & Events Processing
    // ---------------------------------------------------------

    suspend fun processUsageEvents(startTimeMs: Long, endTimeMs: Long) = withContext(Dispatchers.IO) {
        val mgr = usageStatsManager ?: return@withContext
        if (startTimeMs >= endTimeMs) return@withContext

        // Ensure newly installed apps are tracked
        syncInstalledApps()

        val events = mgr.queryEvents(startTimeMs, endTimeMs)
        val event = UsageEvents.Event()

        // Accumulate durations per (packageName, usageDate)
        val durationMap = mutableMapOf<Pair<String, String>, Long>()

        // Carry forward any sessions already open prior to this tick
        val openSessions = mutableMapOf<String, Long>()
        synchronized(activeOpenSessions) {
            openSessions.putAll(activeOpenSessions)
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            val eventType = event.eventType
            val eventTime = event.timeStamp

            // Consider both modern (ACTIVITY_RESUMED/PAUSED) and classic event pairs
            val isResume = (eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    eventType == 1 /* UsageEvents.Event.MOVE_TO_FOREGROUND */)
            val isPause = (eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                    eventType == 2 /* UsageEvents.Event.MOVE_TO_BACKGROUND */ ||
                    eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE ||
                    eventType == UsageEvents.Event.KEYGUARD_SHOWN)

            if (isResume) {
                openSessions[pkg] = eventTime
            } else if (isPause) {
                val resumeTime = openSessions.remove(pkg)
                if (resumeTime != null && eventTime > resumeTime) {
                    val durationMs = eventTime - resumeTime
                    val usageDate = UsageDayCalculator.getUsageDate(resumeTime)
                    val key = Pair(pkg, usageDate)
                    durationMap[key] = (durationMap[key] ?: 0L) + durationMs
                }
            }
        }

        // For any app still in the foreground at endTimeMs
        for ((pkg, resumeTime) in openSessions) {
            if (endTimeMs > resumeTime) {
                val durationMs = endTimeMs - resumeTime
                val usageDate = UsageDayCalculator.getUsageDate(resumeTime)
                val key = Pair(pkg, usageDate)
                durationMap[key] = (durationMap[key] ?: 0L) + durationMs
            }
        }

        // Save open sessions back to memory with updated baseline
        synchronized(activeOpenSessions) {
            activeOpenSessions.clear()
            for (pkg in openSessions.keys) {
                activeOpenSessions[pkg] = endTimeMs
            }
        }

        val now = System.currentTimeMillis()
        // Commit usage to database with fractional millisecond preservation
        for ((key, durationMs) in durationMap) {
            val (pkg, usageDate) = key
            val totalMs = (uncommittedMillis[key] ?: 0L) + durationMs
            val durationMinutes = totalMs / 60000L
            val remainderMs = totalMs % 60000L
            uncommittedMillis[key] = remainderMs

            if (durationMinutes > 0L) {
                dailyUsageDao.addUsageMinutes(pkg, usageDate, durationMinutes, now)
            }
        }

        // Advance last_synced_at
        appConfigDao.set(AppConfig("last_synced_at", endTimeMs.toString()))
    }

    /**
     * Backfills historical daily usage for the last 30 days if the database has no past records.
     * Uses UsageStatsManager.queryUsageStats so that the user immediately gets real charts on install.
     */
    suspend fun backfillHistoricalDataIfEmpty() = withContext(Dispatchers.IO) {
        val mgr = usageStatsManager ?: return@withContext
        val existingCount = dailyUsageDao.getRowCount()
        if (existingCount > 5) return@withContext // Already populated

        syncInstalledApps()

        val recentDates = UsageDayCalculator.getRecentUsageDates(30)
        val todayStr = UsageDayCalculator.getTodayUsageDate()
        val now = System.currentTimeMillis()
        val newUsages = mutableListOf<DailyUsage>()

        for (dateStr in recentDates) {
            if (dateStr == todayStr) continue // Today is populated via queryEvents in reconcileGaps
            val (startMs, endMs) = UsageDayCalculator.getUsageDayRange(dateStr)
            val stats = try {
                mgr.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startMs, endMs)
            } catch (_: Exception) {
                emptyList()
            }

            for (stat in stats) {
                val totalTimeMs = stat.totalTimeInForeground
                val minutes = totalTimeMs / 60000L
                if (minutes > 0) {
                    newUsages.add(
                        DailyUsage(
                            packageName = stat.packageName,
                            usageDate = dateStr,
                            durationMinutes = minutes,
                            lastUpdatedAt = now
                        )
                    )
                }
            }
        }

        if (newUsages.isNotEmpty()) {
            dailyUsageDao.insertAll(newUsages)
        }
    }

    /**
     * Reconciles usage gaps since the last sync time.
     * If this is first launch or a gap > 6 min, reads system events up to current time.
     * Defaults to beginning of today's usage-day (4:00 AM) so all of today's usage is captured!
     */
    suspend fun reconcileGaps() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val today = UsageDayCalculator.getTodayUsageDate()
        val (todayStartMs, _) = UsageDayCalculator.getUsageDayRange(today)

        val lastSyncedStr = appConfigDao.get("last_synced_at")
        val lastSynced = lastSyncedStr?.toLongOrNull()

        // First run or past day: start from 4 AM today
        val syncStart = if (lastSynced == null || lastSynced < todayStartMs) {
            todayStartMs
        } else {
            lastSynced
        }

        if (now - syncStart > 60 * 1000L) { // If more than 1 minute gap
            processUsageEvents(syncStart, now)
        }

        // Backfill 30 days if needed
        backfillHistoricalDataIfEmpty()
    }

    /**
     * Computes today's usage distribution across 6 4-hour intervals for Screen 9:
     * 4AM, 8AM, 12PM, 4PM, 8PM, 12AM.
     */
    suspend fun getTodayHourlyUsage(): List<com.yu.syncon.ui.components.BarChartItem> = withContext(Dispatchers.IO) {
        val mgr = usageStatsManager ?: return@withContext emptyList()
        val today = UsageDayCalculator.getTodayUsageDate()
        val (startOfDayMs, _) = UsageDayCalculator.getUsageDayRange(today)
        val now = System.currentTimeMillis()

        // 6 4-hour buckets
        val bucketLabels = listOf("4AM", "8AM", "12PM", "4PM", "8PM", "12AM")
        val bucketDurations = LongArray(6) { 0L }
        val bucketDurationMs = 4 * 60 * 60 * 1000L // 4 hours in ms

        val currentBucketIndex = ((now - startOfDayMs) / bucketDurationMs).toInt().coerceIn(0, 5)

        val events = try {
            mgr.queryEvents(startOfDayMs, now)
        } catch (_: Exception) {
            null
        }

        if (events != null) {
            val event = UsageEvents.Event()
            val sessions = mutableMapOf<String, Long>()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                val time = event.timeStamp
                val isResume = (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED || event.eventType == 1)
                val isPause = (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED || event.eventType == 2)

                if (isResume) {
                    sessions[pkg] = time
                } else if (isPause) {
                    val resumeTime = sessions.remove(pkg)
                    if (resumeTime != null && time > resumeTime) {
                        val duration = time - resumeTime
                        val bIdx = ((resumeTime - startOfDayMs) / bucketDurationMs).toInt().coerceIn(0, 5)
                        bucketDurations[bIdx] += duration
                    }
                }
            }

            for ((_, resumeTime) in sessions) {
                if (now > resumeTime) {
                    val duration = now - resumeTime
                    val bIdx = ((resumeTime - startOfDayMs) / bucketDurationMs).toInt().coerceIn(0, 5)
                    bucketDurations[bIdx] += duration
                }
            }
        }

        bucketLabels.mapIndexed { idx, label ->
            val minutes = bucketDurations[idx] / 60000L
            com.yu.syncon.ui.components.BarChartItem(
                label = label,
                valueMinutes = minutes,
                isHighlighted = idx == currentBucketIndex
            )
        }
    }

    // ---------------------------------------------------------
    // Limit Checking & Blocking Logic
    // ---------------------------------------------------------

    data class LimitCheckResult(
        val shouldBlock: Boolean,
        val shouldWarn: Boolean,
        val remainingMinutes: Int,
        val limitMinutes: Int,
        val usedMinutes: Int,
        val blockingStyle: String,
        val snoozeMinutes: Int,
        val appName: String
    )

    suspend fun checkAppLimit(packageName: String): LimitCheckResult? = withContext(Dispatchers.IO) {
        val settings = appLimitSettingsDao.getSettings(packageName) ?: return@withContext null
        if (!settings.isEnabled || settings.dailyLimitMinutes == null) return@withContext null

        val limit = settings.dailyLimitMinutes
        val today = UsageDayCalculator.getTodayUsageDate()
        val dailyState = appDailyStateDao.getOrCreateState(packageName, today)
        val usage = dailyUsageDao.getUsage(packageName, today)
        var usedMinutes = usage?.durationMinutes?.toInt() ?: 0

        // Include current in-progress foreground session minutes if checking the active app
        if (packageName == currentForegroundPackage && currentForegroundSessionStartMs > 0L) {
            val liveMs = System.currentTimeMillis() - currentForegroundSessionStartMs
            if (liveMs > 0) {
                usedMinutes += (liveMs / 60000L).toInt()
            }
        }

        val effectiveLimit = limit + dailyState.extraSnoozeMinutesUsedToday
        val remaining = effectiveLimit - usedMinutes

        val shouldBlock = usedMinutes >= effectiveLimit
        val shouldWarn = remaining in 1..5 && !dailyState.warningShown

        val appName = appInfoDao.getApp(packageName)?.appName ?: packageName

        LimitCheckResult(
            shouldBlock = shouldBlock,
            shouldWarn = shouldWarn,
            remainingMinutes = remaining.coerceAtLeast(0),
            limitMinutes = effectiveLimit,
            usedMinutes = usedMinutes,
            blockingStyle = settings.blockingStyle,
            snoozeMinutes = settings.snoozeMinutes,
            appName = appName
        )
    }

    suspend fun markWarningShown(packageName: String) = withContext(Dispatchers.IO) {
        val today = UsageDayCalculator.getTodayUsageDate()
        appDailyStateDao.setWarningShown(packageName, today, true)
        blockEventDao.insert(
            BlockEvent(
                packageName = packageName,
                usageDate = today,
                eventType = "WARNING_SHOWN",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun markAppBlocked(packageName: String) = withContext(Dispatchers.IO) {
        val today = UsageDayCalculator.getTodayUsageDate()
        appDailyStateDao.setIsBlocked(packageName, today, true)
        blockEventDao.insert(
            BlockEvent(
                packageName = packageName,
                usageDate = today,
                eventType = "BLOCKED",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun snoozeApp(packageName: String, extraMinutes: Int) = withContext(Dispatchers.IO) {
        val today = UsageDayCalculator.getTodayUsageDate()
        appDailyStateDao.addSnoozeMinutes(packageName, today, extraMinutes)
        blockEventDao.insert(
            BlockEvent(
                packageName = packageName,
                usageDate = today,
                eventType = "SNOOZED",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    // ---------------------------------------------------------
    // Limit Configuration CRUD
    // ---------------------------------------------------------

    fun getLimitSettingsFlow(packageName: String): Flow<AppLimitSettings?> =
        appLimitSettingsDao.getSettingsFlow(packageName)

    suspend fun getLimitSettings(packageName: String): AppLimitSettings? = withContext(Dispatchers.IO) {
        appLimitSettingsDao.getSettings(packageName)
    }

    suspend fun saveLimitSettings(settings: AppLimitSettings) = withContext(Dispatchers.IO) {
        appLimitSettingsDao.upsert(settings)
    }

    suspend fun deleteLimitSettings(packageName: String) = withContext(Dispatchers.IO) {
        appLimitSettingsDao.delete(packageName)
    }

    // ---------------------------------------------------------
    // Daily Reset & Data Pruning (4:00 AM)
    // ---------------------------------------------------------

    suspend fun performDailyReset() = withContext(Dispatchers.IO) {
        val today = UsageDayCalculator.getTodayUsageDate()
        // Clear all temporary daily state flags for the new day
        appDailyStateDao.resetAllDailyFlags()

        // 3-year data retention (1095 days)
        val cutoffDate = LocalDate.parse(today, DateTimeFormatter.ISO_LOCAL_DATE)
            .minusDays(1095)
            .format(DateTimeFormatter.ISO_LOCAL_DATE)

        dailyUsageDao.deleteOlderThan(cutoffDate)
        blockEventDao.deleteOlderThan(cutoffDate)
    }

    // ---------------------------------------------------------
    // Dashboard & Trends Queries
    // ---------------------------------------------------------

    fun getTodayUsageFlow(): Flow<List<DailyUsage>> {
        val today = UsageDayCalculator.getTodayUsageDate()
        return dailyUsageDao.getUsageForDateFlow(today)
    }

    fun getAllLimitSettingsFlow(): Flow<List<AppLimitSettings>> =
        appLimitSettingsDao.getAllActiveSettingsFlow()

    fun getYesterdayTotalMinutesFlow(): Flow<Long?> {
        val today = UsageDayCalculator.getTodayUsageDate()
        val yesterday = LocalDate.parse(today, DateTimeFormatter.ISO_LOCAL_DATE).minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
        return dailyUsageDao.getTotalMinutesForDateFlow(yesterday)
    }

    fun getTodayTotalMinutesFlow(): Flow<Long?> {
        val today = UsageDayCalculator.getTodayUsageDate()
        return dailyUsageDao.getTotalMinutesForDateFlow(today)
    }

    suspend fun getUsageForDates(dates: List<String>): Map<String, Long> = withContext(Dispatchers.IO) {
        if (dates.isEmpty()) return@withContext emptyMap()
        val startDate = dates.minOrNull() ?: return@withContext emptyMap()
        val endDate = dates.maxOrNull() ?: return@withContext emptyMap()
        val totals = dailyUsageDao.getUsageTotalsByDate(startDate, endDate).associate { it.usageDate to it.totalMinutes }
        dates.associateWith { date -> totals[date] ?: 0L }
    }

    suspend fun getAppUsageForDates(packageName: String, dates: List<String>): Map<String, Long> = withContext(Dispatchers.IO) {
        if (dates.isEmpty()) return@withContext emptyMap()
        val startDate = dates.minOrNull() ?: return@withContext emptyMap()
        val endDate = dates.maxOrNull() ?: return@withContext emptyMap()
        val usages = dailyUsageDao.getAppUsageBetweenDates(packageName, startDate, endDate).associate { it.usageDate to it.durationMinutes }
        dates.associateWith { date -> usages[date] ?: 0L }
    }

    suspend fun getTopAppsBetweenDates(startDate: String, endDate: String): List<Pair<AppInfo, Long>> =
        withContext(Dispatchers.IO) {
            val appTotals = dailyUsageDao.getTopAppTotalsBetweenDates(startDate, endDate)
            if (appTotals.isEmpty()) return@withContext emptyList()
            val apps = appInfoDao.getAllStatic().associateBy { it.packageName }
            appTotals.mapNotNull { total ->
                if (total.totalMinutes <= 0) return@mapNotNull null
                val app = apps[total.packageName] ?: AppInfo(total.packageName, total.packageName, "Other")
                Pair(app, total.totalMinutes)
            }
        }
}

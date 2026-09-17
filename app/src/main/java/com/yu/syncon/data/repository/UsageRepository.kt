package com.yu.syncon.data.repository

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.yu.syncon.data.local.AppDatabase
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
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context)
) {
    private val appInfoDao = database.appInfoDao()
    private val dailyUsageDao = database.dailyUsageDao()
    private val appLimitSettingsDao = database.appLimitSettingsDao()
    private val appDailyStateDao = database.appDailyStateDao()
    private val blockEventDao = database.blockEventDao()
    private val appConfigDao = database.appConfigDao()

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    // ---------------------------------------------------------
    // Installed Apps Sync
    // ---------------------------------------------------------

    suspend fun syncInstalledApps() = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
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
    // Usage Tracking & Events Processing
    // ---------------------------------------------------------

    suspend fun processUsageEvents(startTimeMs: Long, endTimeMs: Long) = withContext(Dispatchers.IO) {
        val mgr = usageStatsManager ?: return@withContext
        if (startTimeMs >= endTimeMs) return@withContext

        // Ensure newly installed apps are tracked
        syncInstalledApps()

        val events = mgr.queryEvents(startTimeMs, endTimeMs)
        val event = UsageEvents.Event()

        // Track foreground sessions per package: packageName -> lastResumeTimestamp
        val openSessions = mutableMapOf<String, Long>()
        // Accumulate durations per (packageName, usageDate)
        val durationMap = mutableMapOf<Pair<String, String>, Long>()

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

        val now = System.currentTimeMillis()
        // Commit usage to database
        for ((key, durationMs) in durationMap) {
            val (pkg, usageDate) = key
            val durationMinutes = durationMs / 60000L
            if (durationMinutes > 0L) {
                dailyUsageDao.addUsageMinutes(pkg, usageDate, durationMinutes, now)
            }
        }

        // Advance last_synced_at
        appConfigDao.set(AppConfig("last_synced_at", endTimeMs.toString()))
    }

    suspend fun reconcileGaps() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val lastSyncedStr = appConfigDao.get("last_synced_at")
        val lastSynced = lastSyncedStr?.toLongOrNull() ?: (now - 5 * 60 * 1000L)

        // If more than 6 minutes elapsed since last sync, backfill the gap
        if (now - lastSynced > 6 * 60 * 1000L) {
            processUsageEvents(lastSynced, now)
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
        val usedMinutes = usage?.durationMinutes?.toInt() ?: 0

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
        val usages = dailyUsageDao.getUsageBetweenDatesStatic(startDate, endDate)
        val resultMap = mutableMapOf<String, Long>()
        for (date in dates) {
            resultMap[date] = usages.filter { it.usageDate == date }.sumOf { it.durationMinutes }
        }
        resultMap
    }

    suspend fun getTopAppsBetweenDates(startDate: String, endDate: String): List<Pair<AppInfo, Long>> =
        withContext(Dispatchers.IO) {
            val usages = dailyUsageDao.getUsageBetweenDatesStatic(startDate, endDate)
            val apps = appInfoDao.getAllStatic().associateBy { it.packageName }
            usages.groupBy { it.packageName }
                .mapNotNull { (pkg, list) ->
                    val app = apps[pkg] ?: AppInfo(pkg, pkg, "Other")
                    val totalMinutes = list.sumOf { it.durationMinutes }
                    if (totalMinutes > 0) Pair(app, totalMinutes) else null
                }
                .sortedByDescending { it.second }
        }
}

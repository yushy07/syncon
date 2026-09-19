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
import com.yu.syncon.data.local.dao.UsageIntervalDao
import com.yu.syncon.data.local.entity.AppConfig
import com.yu.syncon.data.local.entity.AppDailyState
import com.yu.syncon.data.local.entity.AppInfo
import com.yu.syncon.data.local.entity.AppLimitSettings
import com.yu.syncon.data.local.entity.BlockEvent
import com.yu.syncon.data.local.entity.DailyUsage
import com.yu.syncon.data.local.entity.UsageInterval
import com.yu.syncon.util.CategoryMapper
import com.yu.syncon.util.UsageDayCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class UsageRepository(
    private val context: Context? = null,
    private val database: AppDatabase? = context?.let { AppDatabase.getInstance(it) },
    private val appInfoDao: AppInfoDao = database?.appInfoDao() ?: error("appInfoDao required"),
    private val dailyUsageDao: DailyUsageDao = database?.dailyUsageDao() ?: error("dailyUsageDao required"),
    private val appLimitSettingsDao: AppLimitSettingsDao = database?.appLimitSettingsDao() ?: error("appLimitSettingsDao required"),
    private val appDailyStateDao: AppDailyStateDao = database?.appDailyStateDao() ?: error("appDailyStateDao required"),
    private val blockEventDao: BlockEventDao = database?.blockEventDao() ?: error("blockEventDao required"),
    private val appConfigDao: AppConfigDao = database?.appConfigDao() ?: error("appConfigDao required"),
    private val usageIntervalDao: UsageIntervalDao = database?.usageIntervalDao() ?: error("usageIntervalDao required"),
    private val currentTimeMillis: () -> Long = System::currentTimeMillis
) {

    private val usageStatsManager: UsageStatsManager? by lazy {
        context?.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    }
    private val installationIdMutex = Mutex()

    // ---------------------------------------------------------
    // Installed Apps Sync
    // ---------------------------------------------------------

    suspend fun syncInstalledApps() = withContext(Dispatchers.IO) {
        initFirstLaunchDateIfNeeded()
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
    
    @Volatile
    var currentForegroundPackage: String? = null
        private set
    @Volatile
    var currentForegroundSessionStartMs: Long = 0L
        private set

    fun setCurrentForegroundApp(packageName: String?) {
        val now = currentTimeMillis()
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
        val intervals = mutableListOf<UsageInterval>()
        val installationId = getOrCreateInstallationId()

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
                    addIntervalToDurationMap(durationMap, intervals, installationId, pkg, resumeTime, eventTime)
                }
            }
        }

        // For any app still in the foreground at endTimeMs
        for ((pkg, resumeTime) in openSessions) {
            if (endTimeMs > resumeTime) {
                addIntervalToDurationMap(durationMap, intervals, installationId, pkg, resumeTime, endTimeMs)
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
        // Commit exact milliseconds. durationMinutes remains a derived compatibility value for
        // the current UI while durationMillis is authoritative for future cross-device totals.
        for ((key, durationMs) in durationMap) {
            val (pkg, usageDate) = key
            if (durationMs > 0L) {
                dailyUsageDao.addUsageMillis(pkg, usageDate, durationMs, now)
            }
        }
        if (intervals.isNotEmpty()) {
            usageIntervalDao.insertAll(intervals)
        }

        // Advance last_synced_at
        appConfigDao.set(AppConfig("last_synced_at", endTimeMs.toString()))
    }

    private fun addIntervalToDurationMap(
        durationMap: MutableMap<Pair<String, String>, Long>,
        intervals: MutableList<UsageInterval>,
        installationId: String,
        packageName: String,
        startTimeMs: Long,
        endTimeMs: Long
    ) {
        var cursor = startTimeMs
        val zoneId = ZoneId.systemDefault()
        while (cursor < endTimeMs) {
            val usageDate = UsageDayCalculator.getUsageDate(cursor, zoneId)
            val (_, usageDayEndMs) = UsageDayCalculator.getUsageDayRange(usageDate, zoneId)
            val segmentEnd = minOf(endTimeMs, usageDayEndMs)
            if (segmentEnd <= cursor) break

            val durationMs = segmentEnd - cursor
            val key = Pair(packageName, usageDate)
            durationMap[key] = (durationMap[key] ?: 0L) + durationMs

            val stableKey = "$installationId|$packageName|$cursor|$segmentEnd"
            val timestamp = currentTimeMillis()
            intervals += UsageInterval(
                recordId = UUID.nameUUIDFromBytes(stableKey.toByteArray(Charsets.UTF_8)).toString(),
                installationId = installationId,
                sourceIdentifier = packageName,
                usageDate = usageDate,
                startTimeUtc = cursor,
                endTimeUtc = segmentEnd,
                durationMillis = durationMs,
                timezoneId = zoneId.id,
                utcOffsetMinutes = Instant.ofEpochMilli(cursor).atZone(zoneId).offset.totalSeconds / 60,
                createdAtUtc = timestamp,
                updatedAtUtc = timestamp
            )
            cursor = segmentEnd
        }
    }

    /**
     * Splits a foreground interval at each 4:00 AM usage-day boundary. Polling usually
     * limits the error to a few minutes, but storing the interval correctly here keeps
     * daily limits, trends, and future cross-device summaries deterministic.
     */
    internal fun splitDurationByUsageDate(startTimeMs: Long, endTimeMs: Long): Map<String, Long> {
        if (startTimeMs >= endTimeMs) return emptyMap()

        val result = linkedMapOf<String, Long>()
        var cursor = startTimeMs
        while (cursor < endTimeMs) {
            val usageDate = UsageDayCalculator.getUsageDate(cursor)
            val (_, usageDayEndMs) = UsageDayCalculator.getUsageDayRange(usageDate)
            val segmentEnd = minOf(endTimeMs, usageDayEndMs)
            if (segmentEnd <= cursor) break

            result[usageDate] = (result[usageDate] ?: 0L) + (segmentEnd - cursor)
            cursor = segmentEnd
        }
        return result
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
        appConfigDao.set(AppConfig("last_reconciled_at", now.toString()))
    }

    data class TrackingHealthSnapshot(
        val lastCollectionAt: Long?,
        val lastReconciliationAt: Long?
    )

    suspend fun getTrackingHealthSnapshot(): TrackingHealthSnapshot = withContext(Dispatchers.IO) {
        TrackingHealthSnapshot(
            lastCollectionAt = appConfigDao.get("last_synced_at")?.toLongOrNull(),
            lastReconciliationAt = appConfigDao.get("last_reconciled_at")?.toLongOrNull()
        )
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
        val settings = appLimitSettingsDao.getSettings(packageName)
        val appInfo = appInfoDao.getApp(packageName)
        val appName = appInfo?.appName ?: packageName
        val category = appInfo?.category ?: "Other"
        val catLimit = getCategoryLimit(category)

        val hasAppLimit = settings != null && settings.isEnabled && settings.dailyLimitMinutes != null
        val hasCatLimit = catLimit != null && catLimit.isEnabled

        if (!hasAppLimit && !hasCatLimit) return@withContext null

        val today = UsageDayCalculator.getTodayUsageDate()
        val dailyState = appDailyStateDao.getOrCreateState(packageName, today)
        val usage = dailyUsageDao.getUsage(packageName, today)
        var usedMinutes = usage?.durationMinutes?.toInt() ?: 0
        var liveSessionMinutes = 0

        // Include current in-progress foreground session minutes if checking the active app
        if (packageName == currentForegroundPackage && currentForegroundSessionStartMs > 0L) {
            val lastUpdatedAt = usage?.lastUpdatedAt ?: 0L
            val elapsedBaselineMs = maxOf(currentForegroundSessionStartMs, lastUpdatedAt)
            val liveMs = currentTimeMillis() - elapsedBaselineMs
            if (liveMs > 0) {
                liveSessionMinutes = (liveMs / 60000L).toInt()
                usedMinutes += liveSessionMinutes
            }
        }

        // 1. Evaluate per-app limit if set
        if (hasAppLimit) {
            val limit = settings.dailyLimitMinutes
            val effectiveLimit = limit + dailyState.extraSnoozeMinutesUsedToday
            val remaining = effectiveLimit - usedMinutes
            val shouldBlock = usedMinutes >= effectiveLimit
            val shouldWarn = remaining in 1..5 && !dailyState.warningShown

            if (shouldBlock || shouldWarn || !hasCatLimit) {
                return@withContext LimitCheckResult(
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
        }

        // 2. Evaluate category budget if set
        if (hasCatLimit) {
            val cat = catLimit
            // The database total can be up to one tracking interval behind. Include only the
            // active app's uncommitted portion so category enforcement is as timely as app limits
            // without counting any duration already persisted by the tracking service.
            val catUsedMinutes = getTodayCategoryUsageMinutes(category).toInt() + liveSessionMinutes
            val remaining = cat.dailyLimitMinutes - catUsedMinutes
            val shouldBlock = catUsedMinutes >= cat.dailyLimitMinutes
            val shouldWarn = remaining in 1..5 && !dailyState.warningShown

            return@withContext LimitCheckResult(
                shouldBlock = shouldBlock,
                shouldWarn = shouldWarn,
                remainingMinutes = remaining.coerceAtLeast(0),
                limitMinutes = cat.dailyLimitMinutes,
                usedMinutes = catUsedMinutes,
                blockingStyle = cat.blockingStyle,
                snoozeMinutes = cat.snoozeMinutes,
                appName = "$appName ($category Limit)"
            )
        }

        return@withContext null
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
        appDailyStateDao.setIsBlocked(packageName, today, false)
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
        usageIntervalDao.deleteOlderThan(cutoffDate)
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

    data class ScreentimePersona(
        val title: String,
        val emoji: String,
        val tagline: String,
        val touchGrassRatioPercent: Int,
        val cleanDaysCount: Int,
        val dailyAverageMinutes: Long,
        val peakDayName: String?,
        val peakDayMinutes: Long,
        val lowestDayName: String?,
        val lowestDayMinutes: Long,
        val peakWindowText: String,
        val peakWindowSharePercent: Int,
        val deltaPercentVsPreviousPeriod: Int
    )

    suspend fun getTrendsInsights(dates: List<String>): ScreentimePersona = withContext(Dispatchers.IO) {
        val usageMap = getUsageForDates(dates)
        val totalMinutes = usageMap.values.sum()
        val dailyAvg = if (dates.isNotEmpty()) totalMinutes / dates.size else 0L

        var peakDayStr: String? = null
        var peakMins = 0L
        var lowestDayStr: String? = null
        var lowestMins = Long.MAX_VALUE

        for ((date, mins) in usageMap) {
            if (mins >= peakMins) {
                peakMins = mins
                peakDayStr = date
            }
            if (mins in 1..lowestMins) {
                lowestMins = mins
                lowestDayStr = date
            }
        }
        if (lowestMins == Long.MAX_VALUE) lowestMins = 0L

        val dayFormatter = DateTimeFormatter.ofPattern("EEE", java.util.Locale.ENGLISH)
        val peakDayName = peakDayStr?.let {
            try { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE).format(dayFormatter) } catch (_: Exception) { null }
        }
        val lowestDayName = lowestDayStr?.let {
            try { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE).format(dayFormatter) } catch (_: Exception) { null }
        }

        // Previous period comparison
        val prevDates = UsageDayCalculator.getRecentUsageDates(dates.size * 2).take(dates.size)
        val prevUsageMap = getUsageForDates(prevDates)
        val prevTotal = prevUsageMap.values.sum()
        val deltaPercent = if (prevTotal > 0) {
            (((totalMinutes - prevTotal).toFloat() / prevTotal) * 100).toInt()
        } else {
            0
        }

        // Touch Grass ratio: waking day = 16 hours (960 min)
        val touchGrassRatio = ((1f - (dailyAvg.toFloat() / 960f).coerceIn(0f, 0.95f)) * 100).toInt()
        val cleanDays = usageMap.count { it.value in 1..180 }

        // Hourly distribution / Peak window
        val hourlyBars = getTodayHourlyUsage()
        val maxHourlyBucket = hourlyBars.maxByOrNull { it.valueMinutes }
        val hourlyTotal = hourlyBars.sumOf { it.valueMinutes }.coerceAtLeast(1L)
        val peakWindowShare = if (maxHourlyBucket != null && hourlyTotal > 0) {
            ((maxHourlyBucket.valueMinutes.toFloat() / hourlyTotal) * 100).toInt()
        } else {
            35
        }

        val peakWindowLabel = when (maxHourlyBucket?.label) {
            "12AM" -> "Late Night Doomscroll (12 AM – 4 AM)"
            "4AM" -> "Early Dawn (4 AM – 8 AM)"
            "8AM" -> "Morning Peak (8 AM – 12 PM)"
            "12PM" -> "Midday Scroller (12 PM – 4 PM)"
            "4PM" -> "Evening Wind-Down (4 PM – 8 PM)"
            "8PM" -> "Night Chill (8 PM – 12 AM)"
            else -> "Evening Prime (6 PM – 10 PM)"
        }

        val startDate = dates.firstOrNull() ?: ""
        val endDate = dates.lastOrNull() ?: ""
        val topApps = getTopAppsBetweenDates(startDate, endDate)
        val categoryMinutes = mutableMapOf<String, Long>()
        for ((app, mins) in topApps) {
            categoryMinutes[app.category] = (categoryMinutes[app.category] ?: 0L) + mins
        }
        val safeTotal = totalMinutes.coerceAtLeast(1L)
        val socialShare = (((categoryMinutes["Social Media"] ?: 0L) + (categoryMinutes["Communication"] ?: 0L)).toFloat() / safeTotal) * 100
        val entertainmentShare = ((categoryMinutes["Entertainment"] ?: 0L).toFloat() / safeTotal) * 100

        val (title, emoji, tagline) = when {
            dailyAvg <= 120 -> Triple("Zen Monk", "🧘", "Grounded, mindful, and touching grass")
            maxHourlyBucket?.label == "12AM" || maxHourlyBucket?.label == "8PM" ->
                Triple("Night Owl", "🦉", "Peak active when the midnight vibe hits")
            socialShare >= 45 -> Triple("Social Butterfly", "💬", "Deep in the DMs & community feeds")
            entertainmentShare >= 40 -> Triple("Marathon Diver", "🎬", "Immersed in streaming & content flow")
            dailyAvg >= 360 -> Triple("Digital Hustler", "⚡", "Always online with hyper velocity")
            else -> Triple("Digital Explorer", "🧭", "Curious, balanced multifaceted mobile lifestyle")
        }

        ScreentimePersona(
            title = title,
            emoji = emoji,
            tagline = tagline,
            touchGrassRatioPercent = touchGrassRatio,
            cleanDaysCount = cleanDays,
            dailyAverageMinutes = dailyAvg,
            peakDayName = peakDayName,
            peakDayMinutes = peakMins,
            lowestDayName = lowestDayName,
            lowestDayMinutes = lowestMins,
            peakWindowText = peakWindowLabel,
            peakWindowSharePercent = peakWindowShare,
            deltaPercentVsPreviousPeriod = deltaPercent
        )
    }

    // ---------------------------------------------------------
    // PRD v2 Addendum: State, Streak & Export Support
    // ---------------------------------------------------------

    suspend fun initFirstLaunchDateIfNeeded() = withContext(Dispatchers.IO) {
        getOrCreateInstallationId()
        val existing = appConfigDao.get("first_launch_date")
        if (existing == null) {
            val today = UsageDayCalculator.getTodayUsageDate()
            appConfigDao.set(AppConfig("first_launch_date", today))
        }
    }

    /**
     * Privacy-safe identity for this app installation. It is deliberately random and is not
     * derived from Android hardware identifiers. A restored backup records its source identity,
     * but never replaces the receiving installation's identity.
     */
    suspend fun getOrCreateInstallationId(): String = installationIdMutex.withLock {
        appConfigDao.get("installation_id")?.let { return@withLock it }
        val newId = UUID.randomUUID().toString()
        appConfigDao.set(AppConfig("installation_id", newId))
        newId
    }

    suspend fun getOrCreateDailyState(packageName: String, usageDate: String): AppDailyState = withContext(Dispatchers.IO) {
        appDailyStateDao.getOrCreateState(packageName, usageDate)
    }

    suspend fun getDailyUsage(packageName: String, usageDate: String): DailyUsage? = withContext(Dispatchers.IO) {
        dailyUsageDao.getUsage(packageName, usageDate)
    }

    suspend fun calculateCleanDayStreak(): Int = withContext(Dispatchers.IO) {
        initFirstLaunchDateIfNeeded()
        val firstLaunchStr = appConfigDao.get("first_launch_date") ?: UsageDayCalculator.getTodayUsageDate()
        val firstLaunchDate = try {
            LocalDate.parse(firstLaunchStr, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: Exception) {
            LocalDate.now()
        }

        val todayStr = UsageDayCalculator.getTodayUsageDate()
        var currentDay = try {
            LocalDate.parse(todayStr, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: Exception) {
            LocalDate.now()
        }

        var streak = 0
        while (!currentDay.isBefore(firstLaunchDate)) {
            val dayStr = currentDay.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val blockedCount = blockEventDao.getBlockedEventCountForDate(dayStr)
            if (blockedCount > 0) {
                break
            }
            streak++
            currentDay = currentDay.minusDays(1)
        }
        streak
    }

    suspend fun exportAllDataAsJson(): String = withContext(Dispatchers.IO) {
        val appInfos = appInfoDao.getAllStatic()
        val dailyUsages = dailyUsageDao.getAllStatic()
        val limitSettings = appLimitSettingsDao.getAllStatic()
        val blockEvents = blockEventDao.getAllStatic()
        val usageIntervals = usageIntervalDao.getAllStatic()
        val categoryLimits = getAllCategoryLimits()

        val root = JSONObject()
        root.put("exported_at", System.currentTimeMillis())
        root.put("export_date", UsageDayCalculator.getTodayUsageDate())
        root.put("version", "1.0.0")
        root.put("backup_kind", "SYNCON_ANDROID_BACKUP")
        root.put("schema_version", 2)
        root.put("source_platform", "ANDROID")
        root.put("source_installation_id", getOrCreateInstallationId())

        val appsArray = JSONArray()
        for (app in appInfos) {
            val obj = JSONObject()
            obj.put("packageName", app.packageName)
            obj.put("appName", app.appName)
            obj.put("category", app.category)
            obj.put("isCategoryManuallySet", app.isCategoryManuallySet)
            obj.put("isSystemApp", app.isSystemApp)
            appsArray.put(obj)
        }
        root.put("app_info", appsArray)

        val usageArray = JSONArray()
        for (usage in dailyUsages) {
            val obj = JSONObject()
            obj.put("packageName", usage.packageName)
            obj.put("usageDate", usage.usageDate)
            obj.put("durationMinutes", usage.durationMinutes)
            obj.put("durationMillis", usage.durationMillis)
            obj.put("lastUpdatedAt", usage.lastUpdatedAt)
            usageArray.put(obj)
        }
        root.put("daily_usage", usageArray)

        val limitsArray = JSONArray()
        for (setting in limitSettings) {
            val obj = JSONObject()
            obj.put("packageName", setting.packageName)
            obj.put("dailyLimitMinutes", setting.dailyLimitMinutes)
            obj.put("blockingStyle", setting.blockingStyle)
            obj.put("snoozeMinutes", setting.snoozeMinutes)
            obj.put("isEnabled", setting.isEnabled)
            limitsArray.put(obj)
        }
        root.put("app_limit_settings", limitsArray)

        val eventsArray = JSONArray()
        for (event in blockEvents) {
            val obj = JSONObject()
            obj.put("id", event.id)
            obj.put("packageName", event.packageName)
            obj.put("usageDate", event.usageDate)
            obj.put("eventType", event.eventType)
            obj.put("timestamp", event.timestamp)
            eventsArray.put(obj)
        }
        root.put("block_event_log", eventsArray)

        val intervalsArray = JSONArray()
        for (interval in usageIntervals) {
            intervalsArray.put(JSONObject().apply {
                put("recordId", interval.recordId)
                put("installationId", interval.installationId)
                put("sourcePlatform", interval.sourcePlatform)
                put("sourceType", interval.sourceType)
                put("sourceIdentifier", interval.sourceIdentifier)
                put("usageDate", interval.usageDate)
                put("startTimeUtc", interval.startTimeUtc)
                put("endTimeUtc", interval.endTimeUtc)
                put("durationMillis", interval.durationMillis)
                put("timezoneId", interval.timezoneId)
                put("utcOffsetMinutes", interval.utcOffsetMinutes)
                put("createdAtUtc", interval.createdAtUtc)
                put("updatedAtUtc", interval.updatedAtUtc)
                put("localRevision", interval.localRevision)
                put("serverRevision", interval.serverRevision)
                put("syncState", interval.syncState)
                put("isDeleted", interval.isDeleted)
            })
        }
        root.put("usage_intervals", intervalsArray)

        val categoryLimitsArray = JSONArray()
        for (setting in categoryLimits) {
            categoryLimitsArray.put(JSONObject().apply {
                put("category", setting.category)
                put("dailyLimitMinutes", setting.dailyLimitMinutes)
                put("blockingStyle", setting.blockingStyle)
                put("snoozeMinutes", setting.snoozeMinutes)
                put("isEnabled", setting.isEnabled)
            })
        }
        root.put("category_limits", categoryLimitsArray)

        root.toString(2)
    }

    // ---------------------------------------------------------
    // App Open / Launch Counts
    // ---------------------------------------------------------

    suspend fun getTodayAppLaunchCounts(): Map<String, Int> = withContext(Dispatchers.IO) {
        val mgr = usageStatsManager ?: return@withContext emptyMap()
        val today = UsageDayCalculator.getTodayUsageDate()
        val (startOfDayMs, _) = UsageDayCalculator.getUsageDayRange(today)
        val now = System.currentTimeMillis()
        val counts = mutableMapOf<String, Int>()
        try {
            val events = mgr.queryEvents(startOfDayMs, now)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val isResume = (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED || event.eventType == 1)
                if (isResume) {
                    val pkg = event.packageName ?: continue
                    counts[pkg] = (counts[pkg] ?: 0) + 1
                }
            }
        } catch (_: Exception) {}
        counts
    }

    // ---------------------------------------------------------
    // Category Budgets / Group Limits
    // ---------------------------------------------------------

    data class CategoryLimitSetting(
        val category: String,
        val dailyLimitMinutes: Int,
        val blockingStyle: String = "STRICT",
        val snoozeMinutes: Int = 5,
        val isEnabled: Boolean = true
    )

    suspend fun getCategoryLimit(category: String): CategoryLimitSetting? = withContext(Dispatchers.IO) {
        val json = appConfigDao.get("cat_limit_$category") ?: return@withContext null
        try {
            val obj = JSONObject(json)
            CategoryLimitSetting(
                category = obj.getString("category"),
                dailyLimitMinutes = obj.getInt("dailyLimitMinutes"),
                blockingStyle = obj.optString("blockingStyle", "STRICT"),
                snoozeMinutes = obj.optInt("snoozeMinutes", 5),
                isEnabled = obj.optBoolean("isEnabled", true)
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveCategoryLimit(setting: CategoryLimitSetting) = withContext(Dispatchers.IO) {
        val obj = JSONObject().apply {
            put("category", setting.category)
            put("dailyLimitMinutes", setting.dailyLimitMinutes)
            put("blockingStyle", setting.blockingStyle)
            put("snoozeMinutes", setting.snoozeMinutes)
            put("isEnabled", setting.isEnabled)
        }
        appConfigDao.set(AppConfig("cat_limit_${setting.category}", obj.toString()))
    }

    suspend fun deleteCategoryLimit(category: String) = withContext(Dispatchers.IO) {
        appConfigDao.delete("cat_limit_$category")
    }

    suspend fun getAllCategoryLimits(): List<CategoryLimitSetting> = withContext(Dispatchers.IO) {
        CategoryMapper.ALL_CATEGORIES.mapNotNull { cat ->
            getCategoryLimit(cat)
        }
    }

    suspend fun getTodayCategoryUsageMinutes(category: String): Long = withContext(Dispatchers.IO) {
        val today = UsageDayCalculator.getTodayUsageDate()
        val usages = dailyUsageDao.getUsageForDateStatic(today)
        val apps = appInfoDao.getAllStatic().associateBy { it.packageName }
        usages.filter { (apps[it.packageName]?.category ?: "Other") == category }
            .sumOf { it.durationMinutes }
    }

    // ---------------------------------------------------------
    // Data Import & Restore
    // ---------------------------------------------------------

    suspend fun importDataFromJson(jsonString: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            val backupKind = root.optString("backup_kind", "LEGACY_SYNCON_ANDROID_BACKUP")
            val schemaVersion = root.optInt("schema_version", 1)
            if (backupKind != "SYNCON_ANDROID_BACKUP" && backupKind != "LEGACY_SYNCON_ANDROID_BACKUP") {
                error("This file is not a SyncOn Android backup")
            }
            if (schemaVersion !in 1..2) {
                error("Unsupported SyncOn backup schema: $schemaVersion")
            }
            var restoredApps = 0
            var restoredUsages = 0
            var restoredLimits = 0
            var restoredIntervals = 0

            // 1. App Infos
            val appsArray = root.optJSONArray("app_info")
            if (appsArray != null) {
                val appList = mutableListOf<AppInfo>()
                for (i in 0 until appsArray.length()) {
                    val obj = appsArray.getJSONObject(i)
                    appList.add(
                        AppInfo(
                            packageName = obj.getString("packageName"),
                            appName = obj.getString("appName"),
                            category = obj.getString("category"),
                            isCategoryManuallySet = obj.optBoolean("isCategoryManuallySet", false),
                            isSystemApp = obj.optBoolean("isSystemApp", false)
                        )
                    )
                }
                if (appList.isNotEmpty()) {
                    appInfoDao.insertOrIgnoreAll(appList)
                    restoredApps = appList.size
                }
            }

            // 2. Daily Usages
            val usagesArray = root.optJSONArray("daily_usage")
            if (usagesArray != null) {
                val usagesList = mutableListOf<DailyUsage>()
                for (i in 0 until usagesArray.length()) {
                    val obj = usagesArray.getJSONObject(i)
                    usagesList.add(
                        DailyUsage(
                            packageName = obj.getString("packageName"),
                            usageDate = obj.getString("usageDate"),
                            durationMinutes = obj.getLong("durationMinutes"),
                            lastUpdatedAt = obj.optLong("lastUpdatedAt", System.currentTimeMillis()),
                            durationMillis = obj.optLong(
                                "durationMillis",
                                obj.getLong("durationMinutes") * 60_000L
                            )
                        )
                    )
                }
                if (usagesList.isNotEmpty()) {
                    dailyUsageDao.insertAll(usagesList)
                    restoredUsages = usagesList.size
                }
            }

            // 3. Limit Settings
            val limitsArray = root.optJSONArray("app_limit_settings")
            if (limitsArray != null) {
                for (i in 0 until limitsArray.length()) {
                    val obj = limitsArray.getJSONObject(i)
                    val limitMinutes = if (obj.has("dailyLimitMinutes") && !obj.isNull("dailyLimitMinutes")) {
                        obj.getInt("dailyLimitMinutes")
                    } else null

                    appLimitSettingsDao.upsert(
                        AppLimitSettings(
                            packageName = obj.getString("packageName"),
                            dailyLimitMinutes = limitMinutes,
                            blockingStyle = obj.optString("blockingStyle", "STRICT"),
                            snoozeMinutes = obj.optInt("snoozeMinutes", 5),
                            isEnabled = obj.optBoolean("isEnabled", true)
                        )
                    )
                    restoredLimits++
                }
            }

            // 4. Category limits
            val categoryLimitsArray = root.optJSONArray("category_limits")
            if (categoryLimitsArray != null) {
                for (i in 0 until categoryLimitsArray.length()) {
                    val obj = categoryLimitsArray.getJSONObject(i)
                    saveCategoryLimit(
                        CategoryLimitSetting(
                            category = obj.getString("category"),
                            dailyLimitMinutes = obj.getInt("dailyLimitMinutes"),
                            blockingStyle = obj.optString("blockingStyle", "STRICT"),
                            snoozeMinutes = obj.optInt("snoozeMinutes", 5),
                            isEnabled = obj.optBoolean("isEnabled", true)
                        )
                    )
                }
            }

            // 5. Precise usage intervals. INSERT IGNORE makes repeat imports idempotent.
            val intervalsArray = root.optJSONArray("usage_intervals")
            if (intervalsArray != null) {
                val intervals = mutableListOf<UsageInterval>()
                for (i in 0 until intervalsArray.length()) {
                    val obj = intervalsArray.getJSONObject(i)
                    intervals += UsageInterval(
                        recordId = obj.getString("recordId"),
                        installationId = obj.getString("installationId"),
                        sourcePlatform = obj.optString("sourcePlatform", "ANDROID"),
                        sourceType = obj.optString("sourceType", "ANDROID_APP"),
                        sourceIdentifier = obj.getString("sourceIdentifier"),
                        usageDate = obj.getString("usageDate"),
                        startTimeUtc = obj.getLong("startTimeUtc"),
                        endTimeUtc = obj.getLong("endTimeUtc"),
                        durationMillis = obj.getLong("durationMillis"),
                        timezoneId = obj.optString("timezoneId", "UTC"),
                        utcOffsetMinutes = obj.optInt("utcOffsetMinutes", 0),
                        createdAtUtc = obj.optLong("createdAtUtc", obj.getLong("startTimeUtc")),
                        updatedAtUtc = obj.optLong("updatedAtUtc", obj.getLong("endTimeUtc")),
                        localRevision = obj.optLong("localRevision", 1L),
                        serverRevision = if (obj.has("serverRevision") && !obj.isNull("serverRevision")) obj.getLong("serverRevision") else null,
                        syncState = "LOCAL_ONLY",
                        isDeleted = obj.optBoolean("isDeleted", false)
                    )
                }
                restoredIntervals = usageIntervalDao.insertAll(intervals).count { it != -1L }
            }

            // 6. Audit events, deduplicated by their immutable event fields.
            val eventsArray = root.optJSONArray("block_event_log")
            if (eventsArray != null) {
                for (i in 0 until eventsArray.length()) {
                    val obj = eventsArray.getJSONObject(i)
                    val packageName = obj.getString("packageName")
                    val usageDate = obj.getString("usageDate")
                    val eventType = obj.getString("eventType")
                    val timestamp = obj.getLong("timestamp")
                    if (blockEventDao.countMatching(packageName, usageDate, eventType, timestamp) == 0) {
                        blockEventDao.insert(BlockEvent(packageName = packageName, usageDate = usageDate, eventType = eventType, timestamp = timestamp))
                    }
                }
            }

            Result.success("Restored $restoredApps apps, $restoredUsages usage records, $restoredIntervals precise intervals, and $restoredLimits app limits.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

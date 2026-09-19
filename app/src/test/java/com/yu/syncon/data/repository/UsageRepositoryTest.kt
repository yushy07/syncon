package com.yu.syncon.data.repository

import com.yu.syncon.data.local.dao.AppConfigDao
import com.yu.syncon.data.local.dao.AppDailyStateDao
import com.yu.syncon.data.local.dao.AppInfoDao
import com.yu.syncon.data.local.dao.AppLimitSettingsDao
import com.yu.syncon.data.local.dao.BlockEventDao
import com.yu.syncon.data.local.dao.DailyUsageDao
import com.yu.syncon.data.local.dao.DateUsageTotal
import com.yu.syncon.data.local.dao.AppUsageTotal
import com.yu.syncon.data.local.entity.AppConfig
import com.yu.syncon.data.local.entity.AppDailyState
import com.yu.syncon.data.local.entity.AppInfo
import com.yu.syncon.data.local.entity.AppLimitSettings
import com.yu.syncon.data.local.entity.BlockEvent
import com.yu.syncon.data.local.entity.DailyUsage
import com.yu.syncon.util.UsageDayCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class UsageRepositoryTest {

    private lateinit var appInfoDao: FakeAppInfoDao
    private lateinit var dailyUsageDao: FakeDailyUsageDao
    private lateinit var appLimitSettingsDao: FakeAppLimitSettingsDao
    private lateinit var appDailyStateDao: FakeAppDailyStateDao
    private lateinit var blockEventDao: FakeBlockEventDao
    private lateinit var appConfigDao: FakeAppConfigDao

    private lateinit var repository: UsageRepository

    @Before
    fun setup() {
        appInfoDao = FakeAppInfoDao()
        dailyUsageDao = FakeDailyUsageDao()
        appLimitSettingsDao = FakeAppLimitSettingsDao()
        appDailyStateDao = FakeAppDailyStateDao()
        blockEventDao = FakeBlockEventDao()
        appConfigDao = FakeAppConfigDao()

        // Insert a test app
        appInfoDao.apps["com.test.app"] = AppInfo("com.test.app", "Test App", "Social Media")

        repository = UsageRepository(
            appInfoDao = appInfoDao,
            dailyUsageDao = dailyUsageDao,
            appLimitSettingsDao = appLimitSettingsDao,
            appDailyStateDao = appDailyStateDao,
            blockEventDao = blockEventDao,
            appConfigDao = appConfigDao
        )
    }

    @Test
    fun `checkAppLimit returns null when no limit is configured`() = runTest {
        val result = repository.checkAppLimit("com.test.app")
        assertNull(result)
    }

    @Test
    fun `checkAppLimit returns null when limit settings are disabled`() = runTest {
        appLimitSettingsDao.upsert(
            AppLimitSettings(packageName = "com.test.app", dailyLimitMinutes = 60, blockingStyle = "STRICT", isEnabled = false)
        )
        val result = repository.checkAppLimit("com.test.app")
        assertNull(result)
    }

    @Test
    fun `checkAppLimit indicates not blocked and no warning when usage is well below limit`() = runTest {
        val today = UsageDayCalculator.getTodayUsageDate()
        appLimitSettingsDao.upsert(
            AppLimitSettings(packageName = "com.test.app", dailyLimitMinutes = 60, blockingStyle = "STRICT")
        )
        dailyUsageDao.insertOrUpdate(
            DailyUsage(packageName = "com.test.app", usageDate = today, durationMinutes = 20, lastUpdatedAt = 1000L)
        )

        val result = repository.checkAppLimit("com.test.app")
        assertNotNull(result)
        assertEquals(40, result!!.remainingMinutes)
        assertFalse(result.shouldBlock)
        assertFalse(result.shouldWarn)
    }

    @Test
    fun `checkAppLimit triggers warning when remaining minutes are between 1 and 5 and not warned`() = runTest {
        val today = UsageDayCalculator.getTodayUsageDate()
        appLimitSettingsDao.upsert(
            AppLimitSettings(packageName = "com.test.app", dailyLimitMinutes = 60, blockingStyle = "STRICT")
        )
        dailyUsageDao.insertOrUpdate(
            DailyUsage(packageName = "com.test.app", usageDate = today, durationMinutes = 57, lastUpdatedAt = 1000L)
        )

        val result = repository.checkAppLimit("com.test.app")
        assertNotNull(result)
        assertEquals(3, result!!.remainingMinutes)
        assertTrue(result.shouldWarn)
        assertFalse(result.shouldBlock)

        // Mark warning shown and recheck
        repository.markWarningShown("com.test.app")
        val recheck = repository.checkAppLimit("com.test.app")
        assertNotNull(recheck)
        assertFalse(recheck!!.shouldWarn) // Warning must not fire again today
    }

    @Test
    fun `checkAppLimit triggers block when usage meets or exceeds limit for STRICT app`() = runTest {
        val today = UsageDayCalculator.getTodayUsageDate()
        appLimitSettingsDao.upsert(
            AppLimitSettings(packageName = "com.test.app", dailyLimitMinutes = 60, blockingStyle = "STRICT")
        )
        dailyUsageDao.insertOrUpdate(
            DailyUsage(packageName = "com.test.app", usageDate = today, durationMinutes = 60, lastUpdatedAt = 1000L)
        )

        val result = repository.checkAppLimit("com.test.app")
        assertNotNull(result)
        assertEquals(0, result!!.remainingMinutes)
        assertTrue(result.shouldBlock)
        assertEquals("STRICT", result.blockingStyle)

        // Verify marking blocked logs an audit event
        repository.markAppBlocked("com.test.app")
        val state = appDailyStateDao.getState("com.test.app", today)
        assertNotNull(state)
        assertTrue(state!!.isBlocked)
        assertEquals(1, blockEventDao.events.count { it.eventType == "BLOCKED" })
    }

    @Test
    fun `snoozing extends effective limit and unblocks app`() = runTest {
        val today = UsageDayCalculator.getTodayUsageDate()
        appLimitSettingsDao.upsert(
            AppLimitSettings(packageName = "com.test.app", dailyLimitMinutes = 60, blockingStyle = "SOFT", snoozeMinutes = 10)
        )
        dailyUsageDao.insertOrUpdate(
            DailyUsage(packageName = "com.test.app", usageDate = today, durationMinutes = 62, lastUpdatedAt = 1000L)
        )

        val initialResult = repository.checkAppLimit("com.test.app")
        assertNotNull(initialResult)
        assertTrue(initialResult!!.shouldBlock)

        // User snoozes 10 minutes
        repository.snoozeApp("com.test.app", 10)

        // Effective limit is now 60 + 10 = 70 minutes; 70 - 62 = 8 minutes remaining
        val postSnoozeResult = repository.checkAppLimit("com.test.app")
        assertNotNull(postSnoozeResult)
        assertEquals(70, postSnoozeResult!!.limitMinutes)
        assertEquals(8, postSnoozeResult.remainingMinutes)
        assertFalse(postSnoozeResult.shouldBlock)

        // Verify snooze audit log
        assertEquals(1, blockEventDao.events.count { it.eventType == "SNOOZED" })
    }

    @Test
    fun `performDailyReset resets ephemeral flags and deletes records older than 1095 days`() = runTest {
        val today = UsageDayCalculator.getTodayUsageDate()
        val oldDate = LocalDate.parse(today, DateTimeFormatter.ISO_LOCAL_DATE)
            .minusDays(1100)
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
        val recentDate = LocalDate.parse(today, DateTimeFormatter.ISO_LOCAL_DATE)
            .minusDays(30)
            .format(DateTimeFormatter.ISO_LOCAL_DATE)

        dailyUsageDao.insertOrUpdate(DailyUsage("com.test.app", oldDate, 50, 1000L))
        dailyUsageDao.insertOrUpdate(DailyUsage("com.test.app", recentDate, 40, 2000L))
        blockEventDao.insert(BlockEvent(packageName = "com.test.app", usageDate = oldDate, eventType = "BLOCKED", timestamp = 1000L))

        // Set daily flags
        appDailyStateDao.insertOrUpdate(
            AppDailyState(packageName = "com.test.app", usageDate = today, warningShown = true, isBlocked = true, extraSnoozeMinutesUsedToday = 15)
        )

        repository.performDailyReset()

        // Daily flags cleared
        val state = appDailyStateDao.getState("com.test.app", today)
        assertNotNull(state)
        assertFalse(state!!.warningShown)
        assertFalse(state.isBlocked)
        assertEquals(0, state.extraSnoozeMinutesUsedToday)

        // Old records deleted, recent retained
        assertNull(dailyUsageDao.getUsage("com.test.app", oldDate))
        assertNotNull(dailyUsageDao.getUsage("com.test.app", recentDate))
        assertEquals(0, blockEventDao.events.count { it.usageDate == oldDate })
    }

    @Test
    fun testCategoryLimitSaveAndEnforcement() = runTest {
        appInfoDao.insertOrIgnore(AppInfo("com.instagram.android", "Instagram", "Social Media"))
        val today = UsageDayCalculator.getTodayUsageDate()

        // 1. Save 60 min limit for Social Media
        repository.saveCategoryLimit(
            UsageRepository.CategoryLimitSetting(
                category = "Social Media",
                dailyLimitMinutes = 60,
                blockingStyle = "STRICT",
                snoozeMinutes = 5,
                isEnabled = true
            )
        )

        val retrieved = repository.getCategoryLimit("Social Media")
        assertNotNull(retrieved)
        assertEquals(60, retrieved!!.dailyLimitMinutes)

        // 2. Usage at 45m -> not blocked
        dailyUsageDao.insertOrUpdate(DailyUsage("com.instagram.android", today, 45, 1000L))
        val result1 = repository.checkAppLimit("com.instagram.android")
        assertNotNull(result1)
        assertFalse(result1!!.shouldBlock)
        assertEquals(15, result1.remainingMinutes)

        // 3. Usage reaches 60m -> blocked
        dailyUsageDao.insertOrUpdate(DailyUsage("com.instagram.android", today, 60, 2000L))
        val result2 = repository.checkAppLimit("com.instagram.android")
        assertNotNull(result2)
        assertTrue(result2!!.shouldBlock)
        assertEquals(0, result2.remainingMinutes)

        // 4. Delete category limit -> checkAppLimit returns null
        repository.deleteCategoryLimit("Social Media")
        val result3 = repository.checkAppLimit("com.instagram.android")
        assertNull(result3)
    }

    @Test
    fun testExportAndImportData() = runTest {
        val today = UsageDayCalculator.getTodayUsageDate()
        appInfoDao.insertOrIgnore(AppInfo("com.test.app", "Test App", "Browser"))
        dailyUsageDao.insertOrUpdate(DailyUsage("com.test.app", today, 35, 1000L))
        appLimitSettingsDao.upsert(AppLimitSettings("com.test.app", 60, "STRICT", 5, true))

        // Export data
        val json = repository.exportAllDataAsJson()
        assertTrue(json.contains("com.test.app"))
        assertTrue(json.contains("Test App"))

        // Clear tables
        appInfoDao.apps.clear()
        dailyUsageDao.usages.clear()
        appLimitSettingsDao.settings.clear()

        assertEquals(0, appInfoDao.apps.size)
        assertEquals(0, dailyUsageDao.usages.size)

        // Import data back
        val importResult = repository.importDataFromJson(json)
        assertTrue(importResult.isSuccess)

        // Verify restoration
        assertEquals(1, appInfoDao.apps.size)
        assertEquals("Test App", appInfoDao.getApp("com.test.app")?.appName)
        assertEquals(35L, dailyUsageDao.getUsage("com.test.app", today)?.durationMinutes)
        assertEquals(60, appLimitSettingsDao.getSettings("com.test.app")?.dailyLimitMinutes)
    }

    @Test
    fun testTrendsInsightsPersonaZenMonk() = runTest {
        val dates = UsageDayCalculator.getRecentUsageDates(7)
        for (d in dates) {
            dailyUsageDao.insertOrUpdate(DailyUsage("com.test.zen", d, 60, 1000L))
        }
        val persona = repository.getTrendsInsights(dates)
        assertEquals("Zen Monk", persona.title)
        assertEquals("🧘", persona.emoji)
        assertEquals(60L, persona.dailyAverageMinutes)
        assertTrue(persona.touchGrassRatioPercent >= 90)
    }

    @Test
    fun testTrendsInsightsPersonaSocialButterfly() = runTest {
        val dates = UsageDayCalculator.getRecentUsageDates(7)
        appInfoDao.insertOrIgnore(AppInfo("com.whatsapp", "WhatsApp", "Social Media"))
        for (d in dates) {
            dailyUsageDao.insertOrUpdate(DailyUsage("com.whatsapp", d, 250, 1000L))
        }
        val persona = repository.getTrendsInsights(dates)
        assertEquals("Social Butterfly", persona.title)
        assertEquals("💬", persona.emoji)
        assertEquals(250L, persona.dailyAverageMinutes)
    }
}

// -------------------------------------------------------------
// Lightweight Test Fakes for DAOs
// -------------------------------------------------------------

class FakeAppInfoDao : AppInfoDao {
    val apps = mutableMapOf<String, AppInfo>()

    override suspend fun insertOrIgnore(appInfo: AppInfo): Long {
        if (!apps.containsKey(appInfo.packageName)) {
            apps[appInfo.packageName] = appInfo
            return 1L
        }
        return -1L
    }

    override suspend fun insertOrIgnoreAll(appInfos: List<AppInfo>): List<Long> {
        return appInfos.map { insertOrIgnore(it) }
    }

    override suspend fun updateCategory(packageName: String, category: String, isManuallySet: Boolean) {
        val existing = apps[packageName] ?: return
        apps[packageName] = existing.copy(category = category, isCategoryManuallySet = isManuallySet)
    }

    override fun getAllFlow(): Flow<List<AppInfo>> = flowOf(apps.values.toList())
    override suspend fun getAllStatic(): List<AppInfo> = apps.values.toList()
    override suspend fun getApp(packageName: String): AppInfo? = apps[packageName]
    override suspend fun delete(packageName: String) { apps.remove(packageName) }
}

class FakeDailyUsageDao : DailyUsageDao {
    val usages = mutableMapOf<Pair<String, String>, DailyUsage>()

    override suspend fun insertOrUpdate(dailyUsage: DailyUsage) {
        usages[Pair(dailyUsage.packageName, dailyUsage.usageDate)] = dailyUsage
    }

    override suspend fun getUsage(packageName: String, usageDate: String): DailyUsage? {
        return usages[Pair(packageName, usageDate)]
    }

    override fun getUsageForDateFlow(usageDate: String): Flow<List<DailyUsage>> {
        return flowOf(usages.values.filter { it.usageDate == usageDate })
    }

    override suspend fun getUsageForDateStatic(usageDate: String): List<DailyUsage> {
        return usages.values.filter { it.usageDate == usageDate }
    }

    override fun getUsageBetweenDatesFlow(startDate: String, endDate: String): Flow<List<DailyUsage>> {
        return flowOf(usages.values.filter { it.usageDate in startDate..endDate })
    }

    override suspend fun getUsageBetweenDatesStatic(startDate: String, endDate: String): List<DailyUsage> {
        return usages.values.filter { it.usageDate in startDate..endDate }
    }

    override suspend fun getAppUsageBetweenDates(packageName: String, startDate: String, endDate: String): List<DailyUsage> {
        return usages.values.filter { it.packageName == packageName && it.usageDate in startDate..endDate }
    }

    override fun getTotalMinutesForDateFlow(usageDate: String): Flow<Long?> {
        val total = usages.values.filter { it.usageDate == usageDate }.sumOf { it.durationMinutes }
        return flowOf(total)
    }

    override suspend fun getTotalMinutesForDateStatic(usageDate: String): Long? {
        return usages.values.filter { it.usageDate == usageDate }.sumOf { it.durationMinutes }
    }

    override suspend fun getUsageTotalsByDate(startDate: String, endDate: String): List<DateUsageTotal> {
        return usages.values
            .filter { it.usageDate in startDate..endDate }
            .groupBy { it.usageDate }
            .map { (date, list) -> DateUsageTotal(date, list.sumOf { it.durationMinutes }) }
    }

    override suspend fun getTopAppTotalsBetweenDates(startDate: String, endDate: String): List<AppUsageTotal> {
        return usages.values
            .filter { it.usageDate in startDate..endDate }
            .groupBy { it.packageName }
            .map { (pkg, list) -> AppUsageTotal(pkg, list.sumOf { it.durationMinutes }) }
            .sortedByDescending { it.totalMinutes }
    }

    override suspend fun deleteOlderThan(cutoffDate: String): Int {
        val keysToRemove = usages.keys.filter { it.second < cutoffDate }
        keysToRemove.forEach { usages.remove(it) }
        return keysToRemove.size
    }

    override suspend fun insertAll(usages: List<DailyUsage>) {
        usages.forEach { insertOrUpdate(it) }
    }

    override suspend fun getRowCount(): Int = usages.size

    override suspend fun getAllStatic(): List<DailyUsage> = usages.values.toList()
}

class FakeAppLimitSettingsDao : AppLimitSettingsDao {
    val settings = mutableMapOf<String, AppLimitSettings>()

    override suspend fun upsert(settings: AppLimitSettings) {
        this.settings[settings.packageName] = settings
    }

    override suspend fun getSettings(packageName: String): AppLimitSettings? = settings[packageName]
    override fun getSettingsFlow(packageName: String): Flow<AppLimitSettings?> = flowOf(settings[packageName])
    override fun getAllActiveSettingsFlow(): Flow<List<AppLimitSettings>> =
        flowOf(settings.values.filter { it.isEnabled })
    override suspend fun getAllActiveSettingsStatic(): List<AppLimitSettings> =
        settings.values.filter { it.isEnabled }
    override suspend fun getAllStatic(): List<AppLimitSettings> = settings.values.toList()
    override suspend fun delete(packageName: String) { settings.remove(packageName) }
}

class FakeAppDailyStateDao : AppDailyStateDao {
    val states = mutableMapOf<Pair<String, String>, AppDailyState>()

    override suspend fun insertOrUpdate(state: AppDailyState) {
        states[Pair(state.packageName, state.usageDate)] = state
    }

    override suspend fun getState(packageName: String, usageDate: String): AppDailyState? {
        return states[Pair(packageName, usageDate)]
    }

    override fun getStateFlow(packageName: String, usageDate: String): Flow<AppDailyState?> {
        return flowOf(states[Pair(packageName, usageDate)])
    }

    override suspend fun setWarningShown(packageName: String, usageDate: String, shown: Boolean) {
        val existing = states[Pair(packageName, usageDate)] ?: AppDailyState(packageName = packageName, usageDate = usageDate)
        states[Pair(packageName, usageDate)] = existing.copy(warningShown = shown)
    }

    override suspend fun setIsBlocked(packageName: String, usageDate: String, blocked: Boolean) {
        val existing = states[Pair(packageName, usageDate)] ?: AppDailyState(packageName = packageName, usageDate = usageDate)
        states[Pair(packageName, usageDate)] = existing.copy(isBlocked = blocked)
    }

    override suspend fun resetDailyFlagsForDate(usageDate: String) {
        states.forEach { (key, state) ->
            if (key.second == usageDate) {
                states[key] = state.copy(warningShown = false, isBlocked = false, extraSnoozeMinutesUsedToday = 0)
            }
        }
    }

    override suspend fun resetAllDailyFlags() {
        states.forEach { (key, state) ->
            states[key] = state.copy(warningShown = false, isBlocked = false, extraSnoozeMinutesUsedToday = 0)
        }
    }
}

class FakeBlockEventDao : BlockEventDao {
    val events = mutableListOf<BlockEvent>()
    private var nextId = 1L

    override suspend fun insert(event: BlockEvent): Long {
        val id = nextId++
        events.add(event.copy(id = id))
        return id
    }

    override fun getEventsForDateFlow(usageDate: String): Flow<List<BlockEvent>> {
        return flowOf(events.filter { it.usageDate == usageDate })
    }

    override fun getEventsForAppFlow(packageName: String): Flow<List<BlockEvent>> {
        return flowOf(events.filter { it.packageName == packageName })
    }

    override suspend fun deleteOlderThan(cutoffDate: String): Int {
        val count = events.count { it.usageDate < cutoffDate }
        events.removeAll { it.usageDate < cutoffDate }
        return count
    }

    override suspend fun getBlockedEventCountForDate(usageDate: String): Int {
        return events.count { it.usageDate == usageDate && it.eventType == "BLOCKED" }
    }

    override suspend fun getAllStatic(): List<BlockEvent> = events.toList()
}

class FakeAppConfigDao : AppConfigDao {
    val config = mutableMapOf<String, String>()

    override suspend fun set(config: AppConfig) {
        this.config[config.key] = config.value
    }

    override suspend fun get(key: String): String? = config[key]
    override suspend fun delete(key: String) { config.remove(key) }
}

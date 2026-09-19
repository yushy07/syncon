package com.yu.syncon.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.yu.syncon.data.local.entity.DailyUsage
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyUsageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(dailyUsage: DailyUsage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(usages: List<DailyUsage>)

    @Query("SELECT COUNT(*) FROM daily_usage")
    suspend fun getRowCount(): Int

    @Query("SELECT * FROM daily_usage WHERE packageName = :packageName AND usageDate = :usageDate LIMIT 1")
    suspend fun getUsage(packageName: String, usageDate: String): DailyUsage?

    @Query("SELECT * FROM daily_usage WHERE usageDate = :usageDate ORDER BY durationMinutes DESC")
    fun getUsageForDateFlow(usageDate: String): Flow<List<DailyUsage>>

    @Query("SELECT * FROM daily_usage WHERE usageDate = :usageDate ORDER BY durationMinutes DESC")
    suspend fun getUsageForDateStatic(usageDate: String): List<DailyUsage>

    @Query("SELECT * FROM daily_usage WHERE usageDate >= :startDate AND usageDate <= :endDate ORDER BY usageDate ASC")
    fun getUsageBetweenDatesFlow(startDate: String, endDate: String): Flow<List<DailyUsage>>

    @Query("SELECT * FROM daily_usage WHERE usageDate >= :startDate AND usageDate <= :endDate ORDER BY usageDate ASC")
    suspend fun getUsageBetweenDatesStatic(startDate: String, endDate: String): List<DailyUsage>

    @Query("SELECT * FROM daily_usage WHERE packageName = :packageName AND usageDate >= :startDate AND usageDate <= :endDate ORDER BY usageDate ASC")
    suspend fun getAppUsageBetweenDates(packageName: String, startDate: String, endDate: String): List<DailyUsage>

    @Query("SELECT SUM(durationMinutes) FROM daily_usage WHERE usageDate = :usageDate")
    fun getTotalMinutesForDateFlow(usageDate: String): Flow<Long?>

    @Query("SELECT SUM(durationMinutes) FROM daily_usage WHERE usageDate = :usageDate")
    suspend fun getTotalMinutesForDateStatic(usageDate: String): Long?

    @Transaction
    suspend fun addUsageMinutes(packageName: String, usageDate: String, additionalMinutes: Long, updatedAt: Long) {
        val existing = getUsage(packageName, usageDate)
        if (existing == null) {
            insertOrUpdate(
                DailyUsage(
                    packageName = packageName,
                    usageDate = usageDate,
                    durationMinutes = additionalMinutes,
                    lastUpdatedAt = updatedAt
                )
            )
        } else {
            insertOrUpdate(
                existing.copy(
                    durationMinutes = existing.durationMinutes + additionalMinutes,
                    lastUpdatedAt = updatedAt
                )
            )
        }
    }

    @Query("SELECT usageDate, SUM(durationMinutes) AS totalMinutes FROM daily_usage WHERE usageDate >= :startDate AND usageDate <= :endDate GROUP BY usageDate")
    suspend fun getUsageTotalsByDate(startDate: String, endDate: String): List<DateUsageTotal>

    @Query("SELECT packageName, SUM(durationMinutes) AS totalMinutes FROM daily_usage WHERE usageDate >= :startDate AND usageDate <= :endDate GROUP BY packageName ORDER BY totalMinutes DESC")
    suspend fun getTopAppTotalsBetweenDates(startDate: String, endDate: String): List<AppUsageTotal>

    @Query("SELECT * FROM daily_usage ORDER BY usageDate DESC")
    suspend fun getAllStatic(): List<DailyUsage>

    @Query("DELETE FROM daily_usage WHERE usageDate < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String): Int
}

data class DateUsageTotal(
    val usageDate: String,
    val totalMinutes: Long
)

data class AppUsageTotal(
    val packageName: String,
    val totalMinutes: Long
)

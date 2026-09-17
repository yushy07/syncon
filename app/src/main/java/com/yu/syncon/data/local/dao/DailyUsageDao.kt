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

    @Query("DELETE FROM daily_usage WHERE usageDate < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String): Int
}

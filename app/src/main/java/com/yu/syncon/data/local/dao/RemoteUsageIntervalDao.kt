package com.yu.syncon.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.yu.syncon.data.local.entity.RemoteUsageInterval

data class RemoteSourceTotal(
    val sourceIdentifier: String,
    val sourceType: String,
    val durationMillis: Long
)

@Dao
interface RemoteUsageIntervalDao {
    @Upsert
    suspend fun upsertAll(intervals: List<RemoteUsageInterval>)

    @Query("SELECT * FROM remote_usage_interval WHERE usageDate = :usageDate AND isDeleted = 0 ORDER BY startTimeUtc")
    suspend fun getForDate(usageDate: String): List<RemoteUsageInterval>

    @Query("SELECT COALESCE(SUM(durationMillis), 0) FROM remote_usage_interval WHERE usageDate = :usageDate AND sourcePlatform = :platform AND isDeleted = 0")
    suspend fun totalForDate(usageDate: String, platform: String): Long

    @Query("SELECT sourceIdentifier, sourceType, SUM(durationMillis) AS durationMillis FROM remote_usage_interval WHERE usageDate = :usageDate AND isDeleted = 0 GROUP BY sourceIdentifier, sourceType ORDER BY durationMillis DESC")
    suspend fun sourceTotalsForDate(usageDate: String): List<RemoteSourceTotal>

    @Query("DELETE FROM remote_usage_interval WHERE usageDate < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String): Int
}

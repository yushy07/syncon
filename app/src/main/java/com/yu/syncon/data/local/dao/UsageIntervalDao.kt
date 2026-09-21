package com.yu.syncon.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yu.syncon.data.local.entity.UsageInterval

@Dao
interface UsageIntervalDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(intervals: List<UsageInterval>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(intervals: List<UsageInterval>)

    @Query("SELECT * FROM usage_interval WHERE syncState IN ('LOCAL_ONLY', 'PENDING_UPLOAD', 'SYNC_FAILED') AND isDeleted = 0 ORDER BY startTimeUtc ASC LIMIT :limit")
    suspend fun getPending(limit: Int): List<UsageInterval>

    @Query("SELECT * FROM usage_interval ORDER BY startTimeUtc ASC")
    suspend fun getAllStatic(): List<UsageInterval>

    @Query("SELECT * FROM usage_interval WHERE usageDate = :usageDate AND isDeleted = 0 ORDER BY startTimeUtc")
    suspend fun getForDate(usageDate: String): List<UsageInterval>

    @Query("UPDATE usage_interval SET syncState = :syncState, serverRevision = :serverRevision, updatedAtUtc = :updatedAtUtc WHERE recordId IN (:recordIds)")
    suspend fun updateSyncState(
        recordIds: List<String>,
        syncState: String,
        serverRevision: Long?,
        updatedAtUtc: Long
    )

    @Query("UPDATE usage_interval SET syncState = 'SYNCED', serverRevision = :serverRevision, updatedAtUtc = :updatedAtUtc WHERE recordId = :recordId")
    suspend fun markAcknowledged(recordId: String, serverRevision: Long, updatedAtUtc: Long)

    @Query("DELETE FROM usage_interval WHERE usageDate < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String): Int
}

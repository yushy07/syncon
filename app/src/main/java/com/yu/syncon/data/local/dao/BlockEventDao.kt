package com.yu.syncon.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yu.syncon.data.local.entity.BlockEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: BlockEvent): Long

    @Query("SELECT * FROM block_event_log WHERE usageDate = :usageDate ORDER BY timestamp DESC")
    fun getEventsForDateFlow(usageDate: String): Flow<List<BlockEvent>>

    @Query("SELECT * FROM block_event_log WHERE packageName = :packageName ORDER BY timestamp DESC")
    fun getEventsForAppFlow(packageName: String): Flow<List<BlockEvent>>

    @Query("SELECT COUNT(*) FROM block_event_log WHERE usageDate = :usageDate AND eventType = 'BLOCKED'")
    suspend fun getBlockedEventCountForDate(usageDate: String): Int

    @Query("SELECT * FROM block_event_log ORDER BY timestamp DESC")
    suspend fun getAllStatic(): List<BlockEvent>

    @Query("SELECT COUNT(*) FROM block_event_log WHERE packageName = :packageName AND usageDate = :usageDate AND eventType = :eventType AND timestamp = :timestamp")
    suspend fun countMatching(packageName: String, usageDate: String, eventType: String, timestamp: Long): Int

    @Query("DELETE FROM block_event_log WHERE usageDate < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String): Int
}

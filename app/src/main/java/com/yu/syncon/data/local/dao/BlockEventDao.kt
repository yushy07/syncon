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

    @Query("DELETE FROM block_event_log WHERE usageDate < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String): Int
}

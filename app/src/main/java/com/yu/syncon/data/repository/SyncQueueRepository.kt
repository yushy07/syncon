package com.yu.syncon.data.repository

import com.yu.syncon.data.local.dao.UsageIntervalDao
import com.yu.syncon.data.local.entity.UsageInterval

/** Local-only synchronization boundary. No network client is connected in this phase. */
class SyncQueueRepository(private val dao: UsageIntervalDao) {
    suspend fun add(intervals: List<UsageInterval>): Int = dao.insertAll(intervals).count { it != -1L }
    suspend fun pending(limit: Int = 500): List<UsageInterval> = dao.getPending(limit)
    suspend fun all(): List<UsageInterval> = dao.getAllStatic()
    suspend fun prune(cutoffDate: String): Int = dao.deleteOlderThan(cutoffDate)

    suspend fun markUploaded(recordIds: List<String>, serverRevision: Long?, updatedAtUtc: Long) {
        if (recordIds.isNotEmpty()) dao.updateSyncState(recordIds, "SYNCED", serverRevision, updatedAtUtc)
    }

    suspend fun markFailed(recordIds: List<String>, updatedAtUtc: Long) {
        if (recordIds.isNotEmpty()) dao.updateSyncState(recordIds, "SYNC_FAILED", null, updatedAtUtc)
    }
}

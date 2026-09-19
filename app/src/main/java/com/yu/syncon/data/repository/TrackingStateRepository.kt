package com.yu.syncon.data.repository

import com.yu.syncon.data.local.dao.AppConfigDao
import com.yu.syncon.data.local.entity.AppConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Owns durable tracker identity and health cursors independently of usage analytics. */
class TrackingStateRepository(
    private val appConfigDao: AppConfigDao,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis
) {
    data class HealthSnapshot(val lastCollectionAt: Long?, val lastReconciliationAt: Long?)

    private val identityMutex = Mutex()

    suspend fun installationId(): String = identityMutex.withLock {
        appConfigDao.get("installation_id")?.let { return@withLock it }
        val id = UUID.randomUUID().toString()
        appConfigDao.set(AppConfig("installation_id", id))
        id
    }

    suspend fun lastSyncedAt(): Long? = appConfigDao.get("last_synced_at")?.toLongOrNull()

    suspend fun markCollection(timestamp: Long) {
        appConfigDao.set(AppConfig("last_synced_at", timestamp.toString()))
    }

    suspend fun markReconciliation(timestamp: Long = currentTimeMillis()) {
        appConfigDao.set(AppConfig("last_reconciled_at", timestamp.toString()))
    }

    suspend fun health(): HealthSnapshot = HealthSnapshot(
        lastCollectionAt = lastSyncedAt(),
        lastReconciliationAt = appConfigDao.get("last_reconciled_at")?.toLongOrNull()
    )
}

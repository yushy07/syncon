package com.yu.syncon.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yu.syncon.data.local.entity.ConnectedInstallationCache
import com.yu.syncon.data.local.entity.SyncConflict
import com.yu.syncon.data.local.entity.SyncCursor
import com.yu.syncon.data.local.entity.SyncUploadAttempt

@Dao
interface SyncMetadataDao {
    @Query("SELECT * FROM sync_cursor WHERE scope = :scope LIMIT 1")
    suspend fun getCursor(scope: String): SyncCursor?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCursor(cursor: SyncCursor)

    @Query("SELECT * FROM connected_installation_cache ORDER BY lastSeenAt DESC")
    suspend fun getInstallations(): List<ConnectedInstallationCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInstallations(items: List<ConnectedInstallationCache>)

    @Query("DELETE FROM connected_installation_cache")
    suspend fun clearInstallations()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConflict(conflict: SyncConflict)

    @Query("SELECT * FROM sync_conflict WHERE resolvedAtUtc IS NULL ORDER BY detectedAtUtc DESC")
    suspend fun getUnresolvedConflicts(): List<SyncConflict>

    @Query("UPDATE sync_conflict SET resolvedAtUtc = :resolvedAtUtc WHERE recordId = :recordId")
    suspend fun resolveConflict(recordId: String, resolvedAtUtc: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttempt(attempt: SyncUploadAttempt)

    @Query("SELECT * FROM sync_upload_attempt WHERE recordId = :recordId LIMIT 1")
    suspend fun getAttempt(recordId: String): SyncUploadAttempt?

    @Query("DELETE FROM sync_upload_attempt WHERE recordId IN (:recordIds)")
    suspend fun deleteAttempts(recordIds: List<String>)

    @Query("DELETE FROM sync_cursor")
    suspend fun clearCursors()
}

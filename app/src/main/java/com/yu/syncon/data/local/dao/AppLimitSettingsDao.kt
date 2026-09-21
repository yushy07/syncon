package com.yu.syncon.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yu.syncon.data.local.entity.AppLimitSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLimitSettingsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: AppLimitSettings)

    @Query("SELECT * FROM app_limit_settings WHERE packageName = :packageName AND isDeleted = 0 LIMIT 1")
    suspend fun getSettings(packageName: String): AppLimitSettings?

    @Query("SELECT * FROM app_limit_settings WHERE packageName = :packageName AND isDeleted = 0 LIMIT 1")
    fun getSettingsFlow(packageName: String): Flow<AppLimitSettings?>

    @Query("SELECT * FROM app_limit_settings WHERE isEnabled = 1 AND isDeleted = 0")
    fun getAllActiveSettingsFlow(): Flow<List<AppLimitSettings>>

    @Query("SELECT * FROM app_limit_settings WHERE isEnabled = 1 AND isDeleted = 0")
    suspend fun getAllActiveSettingsStatic(): List<AppLimitSettings>

    @Query("SELECT * FROM app_limit_settings")
    suspend fun getAllStatic(): List<AppLimitSettings>

    @Query("DELETE FROM app_limit_settings WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("UPDATE app_limit_settings SET isDeleted = 1, isEnabled = 0, syncState = 'LOCAL_ONLY', localRevision = localRevision + 1, updatedAtUtc = :updatedAtUtc WHERE packageName = :packageName")
    suspend fun markDeleted(packageName: String, updatedAtUtc: Long)

    @Query("SELECT * FROM app_limit_settings WHERE recordId = :recordId LIMIT 1")
    suspend fun getByRecordId(recordId: String): AppLimitSettings?

    @Query("UPDATE app_limit_settings SET serverRevision = :serverRevision, syncState = 'SYNCED' WHERE recordId = :recordId")
    suspend fun markAcknowledged(recordId: String, serverRevision: Long)
}

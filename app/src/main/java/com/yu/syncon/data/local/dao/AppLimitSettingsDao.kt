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

    @Query("SELECT * FROM app_limit_settings WHERE packageName = :packageName LIMIT 1")
    suspend fun getSettings(packageName: String): AppLimitSettings?

    @Query("SELECT * FROM app_limit_settings WHERE packageName = :packageName LIMIT 1")
    fun getSettingsFlow(packageName: String): Flow<AppLimitSettings?>

    @Query("SELECT * FROM app_limit_settings WHERE isEnabled = 1")
    fun getAllActiveSettingsFlow(): Flow<List<AppLimitSettings>>

    @Query("SELECT * FROM app_limit_settings WHERE isEnabled = 1")
    suspend fun getAllActiveSettingsStatic(): List<AppLimitSettings>

    @Query("SELECT * FROM app_limit_settings")
    suspend fun getAllStatic(): List<AppLimitSettings>

    @Query("DELETE FROM app_limit_settings WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}

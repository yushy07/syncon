package com.yu.syncon.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.yu.syncon.data.local.entity.AppInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface AppInfoDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(appInfo: AppInfo): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnoreAll(appInfos: List<AppInfo>): List<Long>

    @Upsert
    suspend fun upsert(appInfo: AppInfo)

    @Query("UPDATE app_info SET category = :category, isCategoryManuallySet = :isManuallySet, updatedAtUtc = :updatedAtUtc, localRevision = localRevision + 1, syncState = 'LOCAL_ONLY' WHERE packageName = :packageName")
    suspend fun updateCategory(packageName: String, category: String, updatedAtUtc: Long, isManuallySet: Boolean = true)

    @Query("SELECT * FROM app_info ORDER BY appName ASC")
    fun getAllFlow(): Flow<List<AppInfo>>

    @Query("SELECT * FROM app_info ORDER BY appName ASC")
    suspend fun getAllStatic(): List<AppInfo>

    @Query("SELECT * FROM app_info WHERE packageName = :packageName LIMIT 1")
    suspend fun getApp(packageName: String): AppInfo?

    @Query("DELETE FROM app_info WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}

package com.yu.syncon.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yu.syncon.data.local.entity.AppConfig

@Dao
interface AppConfigDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(config: AppConfig)

    @Query("SELECT value FROM app_config WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Query("DELETE FROM app_config WHERE `key` = :key")
    suspend fun delete(key: String)
}

package com.yu.syncon.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_info")
data class AppInfo(
    @PrimaryKey val packageName: String,
    val appName: String,
    val category: String,
    val isCategoryManuallySet: Boolean = false,
    val isSystemApp: Boolean = false,
    val updatedAtUtc: Long = System.currentTimeMillis(),
    val localRevision: Long = 1L,
    val serverRevision: Long? = null,
    val syncState: String = "LOCAL_ONLY",
    val isDeleted: Boolean = false
)

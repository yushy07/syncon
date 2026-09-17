package com.yu.syncon.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_info")
data class AppInfo(
    @PrimaryKey val packageName: String,
    val appName: String,
    val category: String,
    val isCategoryManuallySet: Boolean = false,
    val isSystemApp: Boolean = false
)

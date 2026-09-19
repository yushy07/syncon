package com.yu.syncon.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_limit_settings",
    foreignKeys = [
        ForeignKey(
            entity = AppInfo::class,
            parentColumns = ["packageName"],
            childColumns = ["packageName"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AppLimitSettings(
    @PrimaryKey val packageName: String,
    val dailyLimitMinutes: Int?,
    val blockingStyle: String,
    val snoozeMinutes: Int = 5,
    val isEnabled: Boolean = true,
    val recordId: String = "android-app-limit:$packageName",
    val updatedAtUtc: Long = System.currentTimeMillis(),
    val localRevision: Long = 0L,
    val serverRevision: Long? = null,
    val syncState: String = "LOCAL_ONLY",
    val isDeleted: Boolean = false
)

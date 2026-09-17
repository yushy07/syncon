package com.yu.syncon.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_daily_state",
    indices = [Index(value = ["packageName", "usageDate"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = AppInfo::class,
            parentColumns = ["packageName"],
            childColumns = ["packageName"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AppDailyState(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val usageDate: String,
    val warningShown: Boolean = false,
    val isBlocked: Boolean = false,
    val extraSnoozeMinutesUsedToday: Int = 0
)

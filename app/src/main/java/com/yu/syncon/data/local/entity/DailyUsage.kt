package com.yu.syncon.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "daily_usage",
    primaryKeys = ["packageName", "usageDate"],
    indices = [Index(value = ["usageDate"])],
    foreignKeys = [
        ForeignKey(
            entity = AppInfo::class,
            parentColumns = ["packageName"],
            childColumns = ["packageName"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DailyUsage(
    val packageName: String,
    val usageDate: String,
    val durationMinutes: Long,
    val lastUpdatedAt: Long,
    /** Authoritative precision used for future cross-device aggregation. */
    val durationMillis: Long = durationMinutes * 60_000L
)

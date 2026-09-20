package com.yu.syncon.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "remote_usage_interval",
    indices = [Index(value = ["usageDate"]), Index(value = ["sourcePlatform", "usageDate"])]
)
data class RemoteUsageInterval(
    @PrimaryKey val recordId: String,
    val installationId: String,
    val sourcePlatform: String,
    val sourceType: String,
    val sourceIdentifier: String,
    val usageDate: String,
    val startTimeUtc: Long,
    val endTimeUtc: Long,
    val durationMillis: Long,
    val timezoneId: String,
    val utcOffsetMinutes: Int,
    val localRevision: Long,
    val serverRevision: Long,
    val isDeleted: Boolean
)

package com.yu.syncon.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cross-platform-ready activity record. Android writes these locally; a future sync layer can
 * upload them without changing the tracking engine or exposing page/app content.
 */
@Entity(
    tableName = "usage_interval",
    indices = [
        Index(value = ["usageDate"]),
        Index(value = ["syncState"]),
        Index(value = ["installationId", "startTimeUtc", "endTimeUtc"])
    ]
)
data class UsageInterval(
    @PrimaryKey val recordId: String,
    val installationId: String,
    val sourcePlatform: String = "ANDROID",
    val sourceType: String = "ANDROID_APP",
    val sourceIdentifier: String,
    val usageDate: String,
    val startTimeUtc: Long,
    val endTimeUtc: Long,
    val durationMillis: Long,
    val timezoneId: String,
    val utcOffsetMinutes: Int,
    val createdAtUtc: Long,
    val updatedAtUtc: Long,
    val localRevision: Long = 1L,
    val serverRevision: Long? = null,
    val syncState: String = "LOCAL_ONLY",
    val isDeleted: Boolean = false
)

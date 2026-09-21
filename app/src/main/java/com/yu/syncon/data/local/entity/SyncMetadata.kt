package com.yu.syncon.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_cursor")
data class SyncCursor(
    @PrimaryKey val scope: String,
    val serverRevision: Long,
    val updatedAtUtc: Long
)

@Entity(tableName = "connected_installation_cache")
data class ConnectedInstallationCache(
    @PrimaryKey val installationId: String,
    val platform: String,
    val displayName: String,
    val clientVersion: String,
    val lastSeenAt: String,
    val isCurrent: Boolean,
    val cachedAtUtc: Long
)

@Entity(tableName = "sync_conflict")
data class SyncConflict(
    @PrimaryKey val recordId: String,
    val collection: String,
    val localPayload: String,
    val serverPayload: String,
    val serverRevision: Long,
    val detectedAtUtc: Long,
    val resolvedAtUtc: Long? = null
)

@Entity(tableName = "sync_upload_attempt")
data class SyncUploadAttempt(
    @PrimaryKey val recordId: String,
    val collection: String,
    val attemptCount: Int,
    val lastAttemptAtUtc: Long,
    val nextAttemptAtUtc: Long,
    val lastError: String?
)

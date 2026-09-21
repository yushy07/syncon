package com.yu.syncon.data.remote

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.yu.syncon.data.local.AppDatabase
import com.yu.syncon.data.local.entity.ConnectedInstallationCache
import com.yu.syncon.data.local.entity.AppInfo
import com.yu.syncon.data.local.entity.AppLimitSettings
import com.yu.syncon.data.local.entity.BlockEvent
import com.yu.syncon.data.local.entity.UsageInterval
import com.yu.syncon.data.local.entity.RemoteUsageInterval
import com.yu.syncon.data.local.entity.SyncConflict
import com.yu.syncon.data.local.entity.SyncCursor
import com.yu.syncon.data.local.entity.SyncUploadAttempt
import com.yu.syncon.data.repository.UsageRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.Base64
import java.util.UUID

data class ConnectedInstallation(
    val installationId: String,
    val platform: String,
    val displayName: String,
    val clientVersion: String,
    val lastSeenAt: String,
    val isCurrent: Boolean
)

class CloudSyncRepository(
    context: Context,
    private val client: SupabaseClient,
    private val usageRepository: UsageRepository,
    private val onSessionChanged: () -> Unit = {}
) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)
    private val preferences = appContext.getSharedPreferences("syncon_cloud_sync", Context.MODE_PRIVATE)
    private val syncMutex = Mutex()

    fun isSignedIn(): Boolean = client.hasSession()

    suspend fun signUp(email: String, password: String, displayName: String): String =
        client.signUp(email, password, displayName).also { onSessionChanged() }

    suspend fun signIn(email: String, password: String) {
        client.signIn(email, password)
        onSessionChanged()
        syncNow()
    }

    suspend fun signOut() {
        client.signOut()
        preferences.edit().clear().apply()
        database.withTransaction {
            database.syncMetadataDao().clearCursors()
            database.syncMetadataDao().clearInstallations()
        }
        onSessionChanged()
    }

    suspend fun deleteAccount(): Boolean {
        val deleted = client.rpc("delete_sync_account_v3") as? Boolean ?: false
        client.clearLocalSession()
        preferences.edit().clear().apply()
        database.withTransaction {
            database.syncMetadataDao().clearCursors()
            database.syncMetadataDao().clearInstallations()
        }
        onSessionChanged()
        return deleted
    }

    suspend fun claimPairing(rawValue: String): String {
        val uri = Uri.parse(rawValue)
        require(uri.scheme == "syncon" && uri.host == "pair") { "This is not a SyncOn pairing code" }
        require(uri.getQueryParameter("v") == "1") { "Unsupported pairing code version" }
        val requestId = uri.getQueryParameter("id") ?: error("Pairing request is missing")
        UUID.fromString(requestId)
        val secret = uri.getQueryParameter("secret") ?: error("Pairing secret is missing")
        val decoded = Base64.getUrlDecoder().decode(secret)
        require(decoded.size == 32) { "Pairing secret is invalid" }
        val installationId = usageRepository.getOrCreateInstallationId()
        val result = client.rpc(
            "claim_pairing_request_v2",
            JSONObject()
                .put("p_request_id", requestId)
                .put("p_secret", secret)
                .put("p_android_installation_id", installationId)
        ) as JSONObject
        val status = result.optString("status")
        if (status != "CONNECTED") error(
            when (status) {
                "EXPIRED" -> "That QR code expired. Generate a new code in Chrome."
                "ALREADY_USED" -> "That QR code was already used."
                "INVALID_SECRET" -> "The pairing code could not be verified."
                "LOCKED" -> "Too many attempts. Generate a new code in Chrome."
                else -> "Chrome could not be connected ($status)."
            }
        )
        syncNow()
        return "Chrome connected successfully"
    }

    suspend fun connectedInstallations(): List<ConnectedInstallation> {
        return runCatching {
            val result = client.rpc("list_connected_installations_v2") as? JSONArray ?: JSONArray()
            val fetched = List(result.length()) { index ->
            val item = result.getJSONObject(index)
            ConnectedInstallation(
                installationId = item.getString("installation_id"),
                platform = item.getString("platform"),
                displayName = item.optString("display_name").ifBlank { item.getString("platform").lowercase().replaceFirstChar(Char::uppercase) },
                clientVersion = item.optString("client_version"),
                lastSeenAt = item.optString("last_seen_at"),
                isCurrent = item.optBoolean("is_current")
            )
            }
            val now = System.currentTimeMillis()
            database.withTransaction {
                database.syncMetadataDao().clearInstallations()
                database.syncMetadataDao().upsertInstallations(fetched.map {
                    ConnectedInstallationCache(
                        it.installationId, it.platform, it.displayName, it.clientVersion,
                        it.lastSeenAt, it.isCurrent, now
                    )
                })
            }
            fetched
        }.getOrElse { error ->
            val cached = database.syncMetadataDao().getInstallations().map {
                ConnectedInstallation(
                    it.installationId, it.platform, it.displayName, it.clientVersion,
                    it.lastSeenAt, it.isCurrent
                )
            }
            if (cached.isEmpty()) throw error else cached
        }
    }

    suspend fun unresolvedConflictCount(): Int =
        database.syncMetadataDao().getUnresolvedConflicts().size

    suspend fun revokeInstallation(installationId: String): Boolean =
        client.rpc("revoke_installation_v2", JSONObject().put("p_installation_id", installationId)) as? Boolean ?: false

    suspend fun syncNow(): Long = syncMutex.withLock {
        if (client.currentSession() == null) return@withLock 0L
        val installationId = usageRepository.getOrCreateInstallationId()
        client.rpc(
            "sync_register_installation_v2",
            JSONObject()
                .put("p_installation_id", installationId)
                .put("p_platform", "ANDROID")
                .put("p_display_name", android.os.Build.MODEL)
                .put("p_client_version", com.yu.syncon.BuildConfig.VERSION_NAME)
        )
        pushIntervals()
        pushState(installationId)
        val cursor = pullAll()
        preferences.edit().putLong("last_sync_at", System.currentTimeMillis()).apply()
        cursor
    }

    fun lastSyncAt(): Long = preferences.getLong("last_sync_at", 0L)

    private suspend fun pushIntervals() {
        val dao = database.usageIntervalDao()
        while (true) {
            val pending = dao.getPending(250)
            if (pending.isEmpty()) break
            val payload = JSONArray()
            pending.forEach { item ->
                payload.put(
                    JSONObject()
                        .put("record_id", item.recordId)
                        .put("installation_id", item.installationId)
                        .put("source_platform", item.sourcePlatform)
                        .put("source_type", item.sourceType)
                        .put("source_identifier", item.sourceIdentifier)
                        .put("usage_date", item.usageDate)
                        .put("start_time_utc", iso(item.startTimeUtc))
                        .put("end_time_utc", iso(item.endTimeUtc))
                        .put("duration_millis", item.durationMillis)
                        .put("timezone_id", item.timezoneId)
                        .put("utc_offset_minutes", item.utcOffsetMinutes)
                        .put("client_created_at", iso(item.createdAtUtc))
                        .put("client_updated_at", iso(item.updatedAtUtc))
                        .put("local_revision", item.localRevision)
                        .put("is_deleted", item.isDeleted)
                )
            }
            val now = System.currentTimeMillis()
            val response = try {
                client.rpc("sync_push_intervals_v3", JSONObject().put("p_intervals", payload)) as JSONObject
            } catch (error: Exception) {
                pending.forEach { item ->
                    val previous = database.syncMetadataDao().getAttempt(item.recordId)
                    val count = (previous?.attemptCount ?: 0) + 1
                    database.syncMetadataDao().upsertAttempt(
                        SyncUploadAttempt(
                            item.recordId, "activity_intervals", count, now,
                            now + (30_000L * (1L shl count.coerceAtMost(6))), error.message
                        )
                    )
                }
                throw error
            }
            val acknowledgements = response.optJSONArray("acknowledgements") ?: JSONArray()
            val acceptedIds = mutableListOf<String>()
            repeat(acknowledgements.length()) { index ->
                val ack = acknowledgements.getJSONObject(index)
                if (ack.optString("status") == "ACCEPTED") {
                    val recordId = ack.getString("record_id")
                    acceptedIds += recordId
                    dao.markAcknowledged(recordId, ack.getLong("server_revision"), now)
                }
            }
            if (acceptedIds.isNotEmpty()) database.syncMetadataDao().deleteAttempts(acceptedIds)
            if (pending.size < 250) break
        }
    }

    private suspend fun pushState(installationId: String) {
        val sources = JSONArray()
        database.appInfoDao().getAllStatic().forEach { app ->
            sources.put(
                JSONObject()
                    .put("source_type", "ANDROID_APP")
                    .put("source_identifier", app.packageName)
                    .put("display_name", app.appName)
                    .put("category", app.category)
                    .put("is_category_manually_set", app.isCategoryManuallySet)
                    .put("client_updated_at", iso(app.updatedAtUtc))
                    .put("local_revision", app.localRevision)
                    .put("is_deleted", app.isDeleted)
            )
        }

        val limits = JSONArray()
        database.appLimitSettingsDao().getAllStatic().forEach { setting ->
            limits.put(
                JSONObject()
                    .put("record_id", setting.recordId)
                    .put("installation_id", installationId)
                    .put("target_type", "SOURCE")
                    .put("source_platform", "ANDROID")
                    .put("target_identifier", setting.packageName)
                    .put("daily_limit_minutes", setting.dailyLimitMinutes ?: JSONObject.NULL)
                    .put("blocking_style", setting.blockingStyle)
                    .put("snooze_minutes", setting.snoozeMinutes)
                    .put("is_enabled", setting.isEnabled)
                    .put("client_updated_at", iso(setting.updatedAtUtc))
                        .put("local_revision", setting.localRevision.coerceAtLeast(1L))
                    .put("base_server_revision", setting.serverRevision ?: 0L)
                    .put("is_deleted", setting.isDeleted)
            )
        }
        usageRepository.getAllCategoryLimits().forEach { setting ->
            limits.put(
                JSONObject()
                    .put("record_id", setting.recordId)
                    .put("installation_id", installationId)
                    .put("target_type", "CATEGORY")
                    .put("source_platform", JSONObject.NULL)
                    .put("target_identifier", setting.category)
                    .put("daily_limit_minutes", setting.dailyLimitMinutes)
                    .put("blocking_style", setting.blockingStyle)
                    .put("snooze_minutes", setting.snoozeMinutes)
                    .put("is_enabled", setting.isEnabled)
                    .put("client_updated_at", iso(setting.updatedAtUtc))
                    .put("local_revision", setting.localRevision.coerceAtLeast(1L))
                    .put("base_server_revision", setting.serverRevision ?: 0L)
                    .put("is_deleted", setting.isDeleted)
            )
        }

        val events = JSONArray()
        database.blockEventDao().getAllStatic().forEach { event ->
            val recordId = UUID.nameUUIDFromBytes(
                "$installationId|${event.packageName}|${event.usageDate}|${event.eventType}|${event.timestamp}".toByteArray()
            ).toString()
            events.put(
                JSONObject()
                    .put("record_id", recordId)
                    .put("installation_id", installationId)
                    .put("source_platform", "ANDROID")
                    .put("source_identifier", event.packageName)
                    .put("category", JSONObject.NULL)
                    .put("usage_date", event.usageDate)
                    .put("event_type", if (event.eventType == "WARNING_SHOWN") "WARNING" else event.eventType)
                    .put("event_time_utc", iso(event.timestamp))
                    .put("extra_minutes", 0)
                    .put("local_revision", 1)
                    .put("is_deleted", false)
            )
        }
        val mappings = JSONArray()
        DEFAULT_ANDROID_MAPPINGS.forEach { (packageName, serviceId) ->
            mappings.put(
                JSONObject()
                    .put("record_id", "default:ANDROID_APP:$packageName")
                    .put("source_type", "ANDROID_APP")
                    .put("source_identifier", packageName)
                    .put("logical_service_id", serviceId)
                    .put("client_updated_at", iso(0L))
                    .put("local_revision", 1)
                    .put("is_deleted", false)
            )
        }
        val result = client.rpc(
            "sync_push_state_v3",
            JSONObject()
                .put("p_sources", sources)
                .put("p_limits", limits)
                .put("p_block_events", events)
                .put("p_source_mappings", mappings)
        ) as JSONObject
        val acknowledgements = result.optJSONArray("limit_acknowledgements") ?: JSONArray()
        repeat(acknowledgements.length()) { index ->
            val ack = acknowledgements.getJSONObject(index)
            val recordId = ack.getString("record_id")
            val revision = ack.getLong("server_revision")
            database.appLimitSettingsDao().markAcknowledged(recordId, revision)
            usageRepository.markCategoryLimitAcknowledged(recordId, revision)
        }
        val conflicts = result.optJSONArray("conflicts") ?: JSONArray()
        repeat(conflicts.length()) { index ->
            val conflict = conflicts.getJSONObject(index)
            val recordId = conflict.getString("record_id")
            val local = (0 until limits.length())
                .map { limits.getJSONObject(it) }
                .firstOrNull { it.optString("record_id") == recordId }
            val server = conflict.getJSONObject("server_record")
            database.syncMetadataDao().upsertConflict(
                SyncConflict(
                    recordId = recordId,
                    collection = "limit_settings",
                    localPayload = local?.toString() ?: "{}",
                    serverPayload = server.toString(),
                    serverRevision = server.getLong("server_revision"),
                    detectedAtUtc = System.currentTimeMillis()
                )
            )
            applyRemoteLimit(server)
        }
    }

    private suspend fun pullAll(): Long {
        var cursor = database.syncMetadataDao().getCursor("account")?.serverRevision ?: 0L
        do {
            val page = client.rpc(
                "sync_pull_v2",
                JSONObject().put("p_after_revision", cursor).put("p_limit", 1000)
            ) as JSONObject
            val nextCursor = page.optLong("next_revision", cursor)
            database.withTransaction {
                applyPull(page)
                database.syncMetadataDao().upsertCursor(
                    SyncCursor("account", nextCursor, System.currentTimeMillis())
                )
            }
            cursor = nextCursor
        } while (page.optBoolean("has_more"))
        return cursor
    }

    private suspend fun applyPull(page: JSONObject) {
        val intervals = page.optJSONArray("intervals") ?: JSONArray()
        val intervalItems = List(intervals.length()) { index ->
            val item = intervals.getJSONObject(index)
            item
        }
        val localIntervals = intervalItems.filter { it.getString("source_platform") == "ANDROID" }.map { item ->
            UsageInterval(
                recordId = item.getString("record_id"),
                installationId = item.getString("installation_id"),
                sourcePlatform = item.getString("source_platform"),
                sourceType = item.getString("source_type"),
                sourceIdentifier = item.getString("source_identifier"),
                usageDate = item.getString("usage_date"),
                startTimeUtc = Instant.parse(item.getString("start_time_utc")).toEpochMilli(),
                endTimeUtc = Instant.parse(item.getString("end_time_utc")).toEpochMilli(),
                durationMillis = item.getLong("duration_millis"),
                timezoneId = item.getString("timezone_id"),
                utcOffsetMinutes = item.getInt("utc_offset_minutes"),
                createdAtUtc = Instant.parse(item.getString("client_created_at")).toEpochMilli(),
                updatedAtUtc = Instant.parse(item.getString("client_updated_at")).toEpochMilli(),
                localRevision = item.getLong("local_revision"),
                serverRevision = item.getLong("server_revision"),
                syncState = "SYNCED",
                isDeleted = item.optBoolean("is_deleted")
            )
        }
        database.usageIntervalDao().upsertAll(localIntervals)
        val remoteIntervals = intervalItems.filter { it.getString("source_platform") != "ANDROID" }.map { item ->
            RemoteUsageInterval(
                recordId = item.getString("record_id"),
                installationId = item.getString("installation_id"),
                sourcePlatform = item.getString("source_platform"),
                sourceType = item.getString("source_type"),
                sourceIdentifier = item.getString("source_identifier"),
                usageDate = item.getString("usage_date"),
                startTimeUtc = Instant.parse(item.getString("start_time_utc")).toEpochMilli(),
                endTimeUtc = Instant.parse(item.getString("end_time_utc")).toEpochMilli(),
                durationMillis = item.getLong("duration_millis"),
                timezoneId = item.getString("timezone_id"),
                utcOffsetMinutes = item.getInt("utc_offset_minutes"),
                localRevision = item.getLong("local_revision"),
                serverRevision = item.getLong("server_revision"),
                isDeleted = item.optBoolean("is_deleted")
            )
        }
        database.remoteUsageIntervalDao().upsertAll(remoteIntervals)

        val sources = page.optJSONArray("sources") ?: JSONArray()
        repeat(sources.length()) { index ->
            val item = sources.getJSONObject(index)
            if (item.optString("source_type") != "ANDROID_APP") return@repeat
            val packageName = item.getString("source_identifier")
            val existing = database.appInfoDao().getApp(packageName)
            database.appInfoDao().upsert(
                AppInfo(
                    packageName = packageName,
                    appName = item.optString("display_name").ifBlank { existing?.appName ?: packageName },
                    category = item.optString("category", "Other"),
                    isCategoryManuallySet = item.optBoolean("is_category_manually_set"),
                    isSystemApp = existing?.isSystemApp ?: false,
                    updatedAtUtc = Instant.parse(item.getString("client_updated_at")).toEpochMilli(),
                    localRevision = item.getLong("local_revision"),
                    serverRevision = item.getLong("server_revision"),
                    syncState = "SYNCED",
                    isDeleted = item.optBoolean("is_deleted")
                )
            )
        }

        val limits = page.optJSONArray("limits") ?: JSONArray()
        repeat(limits.length()) { index ->
            applyRemoteLimit(limits.getJSONObject(index))
        }

        val events = page.optJSONArray("block_events") ?: JSONArray()
        repeat(events.length()) { index ->
            val item = events.getJSONObject(index)
            if (item.optString("source_platform") != "ANDROID" || item.optBoolean("is_deleted")) return@repeat
            val packageName = item.getString("source_identifier")
            if (database.appInfoDao().getApp(packageName) == null) return@repeat
            val usageDate = item.getString("usage_date")
            val eventType = if (item.getString("event_type") == "WARNING") "WARNING_SHOWN" else item.getString("event_type")
            val timestamp = Instant.parse(item.getString("event_time_utc")).toEpochMilli()
            if (database.blockEventDao().countMatching(packageName, usageDate, eventType, timestamp) == 0) {
                database.blockEventDao().insert(BlockEvent(packageName = packageName, usageDate = usageDate, eventType = eventType, timestamp = timestamp))
            }
        }
    }

    private suspend fun applyRemoteLimit(item: JSONObject) {
        val target = item.getString("target_identifier")
        val deleted = item.optBoolean("is_deleted")
        when {
            item.getString("target_type") == "SOURCE" && item.optString("source_platform") == "ANDROID" -> {
                database.appLimitSettingsDao().upsert(
                    AppLimitSettings(
                        packageName = target,
                        dailyLimitMinutes = item.optInt("daily_limit_minutes").takeIf { !item.isNull("daily_limit_minutes") },
                        blockingStyle = item.getString("blocking_style"),
                        snoozeMinutes = item.getInt("snooze_minutes"),
                        isEnabled = item.optBoolean("is_enabled"),
                        recordId = item.getString("record_id"),
                        updatedAtUtc = Instant.parse(item.getString("client_updated_at")).toEpochMilli(),
                        localRevision = item.getLong("local_revision"),
                        serverRevision = item.getLong("server_revision"),
                        syncState = "SYNCED",
                        isDeleted = deleted
                    )
                )
            }
            item.getString("target_type") == "CATEGORY" -> usageRepository.applyRemoteCategoryLimit(
                UsageRepository.CategoryLimitSetting(
                    category = target,
                    dailyLimitMinutes = item.optInt("daily_limit_minutes", 1),
                    blockingStyle = item.getString("blocking_style"),
                    snoozeMinutes = item.getInt("snooze_minutes"),
                    isEnabled = item.optBoolean("is_enabled"),
                    recordId = item.getString("record_id"),
                    updatedAtUtc = Instant.parse(item.getString("client_updated_at")).toEpochMilli(),
                    localRevision = item.getLong("local_revision"),
                    serverRevision = item.getLong("server_revision"),
                    syncState = "SYNCED",
                    isDeleted = deleted
                )
            )
        }
    }

    private fun iso(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis.coerceAtLeast(0L)).toString()

    companion object {
        private val DEFAULT_ANDROID_MAPPINGS = mapOf(
            "com.google.android.youtube" to "youtube",
            "com.instagram.android" to "instagram",
            "com.facebook.katana" to "facebook",
            "com.reddit.frontpage" to "reddit",
            "com.twitter.android" to "x-twitter",
            "com.netflix.mediaclient" to "netflix",
            "com.spotify.music" to "spotify",
            "com.whatsapp" to "whatsapp",
            "com.discord" to "discord",
            "com.github.android" to "github"
        )
    }
}

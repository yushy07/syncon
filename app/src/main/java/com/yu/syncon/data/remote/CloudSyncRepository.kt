package com.yu.syncon.data.remote

import android.content.Context
import android.net.Uri
import com.yu.syncon.data.local.AppDatabase
import com.yu.syncon.data.local.entity.AppInfo
import com.yu.syncon.data.local.entity.AppLimitSettings
import com.yu.syncon.data.local.entity.BlockEvent
import com.yu.syncon.data.local.entity.UsageInterval
import com.yu.syncon.data.local.entity.RemoteUsageInterval
import com.yu.syncon.data.repository.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    private val usageRepository: UsageRepository
) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)
    private val preferences = appContext.getSharedPreferences("syncon_cloud_sync", Context.MODE_PRIVATE)
    private val syncMutex = Mutex()

    fun isSignedIn(): Boolean = client.hasSession()

    suspend fun signUp(email: String, password: String, displayName: String): String =
        client.signUp(email, password, displayName)

    suspend fun signIn(email: String, password: String) {
        client.signIn(email, password)
        syncNow()
    }

    suspend fun signOut() {
        client.signOut()
        preferences.edit().clear().apply()
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
        val result = client.rpc("list_connected_installations_v2") as? JSONArray ?: return emptyList()
        return List(result.length()) { index ->
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
    }

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
            client.rpc("sync_push_intervals_v2", JSONObject().put("p_intervals", payload))
            dao.updateSyncState(pending.map { it.recordId }, "SYNCED", null, System.currentTimeMillis())
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
        client.rpc(
            "sync_push_state_v2",
            JSONObject()
                .put("p_sources", sources)
                .put("p_limits", limits)
                .put("p_block_events", events)
                .put("p_source_mappings", JSONArray())
        )
    }

    private suspend fun pullAll(): Long {
        var cursor = preferences.getLong("sync_cursor", 0L)
        do {
            val page = client.rpc(
                "sync_pull_v2",
                JSONObject().put("p_after_revision", cursor).put("p_limit", 1000)
            ) as JSONObject
            applyPull(page)
            cursor = page.optLong("next_revision", cursor)
            preferences.edit().putLong("sync_cursor", cursor).apply()
        } while (page.optBoolean("has_more"))
        return cursor
    }

    private suspend fun applyPull(page: JSONObject) = withContext(Dispatchers.IO) {
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
            val item = limits.getJSONObject(index)
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

    private fun iso(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis.coerceAtLeast(0L)).toString()
}

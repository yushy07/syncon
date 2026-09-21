package com.yu.syncon.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yu.syncon.data.local.dao.AppConfigDao
import com.yu.syncon.data.local.dao.AppDailyStateDao
import com.yu.syncon.data.local.dao.AppInfoDao
import com.yu.syncon.data.local.dao.AppLimitSettingsDao
import com.yu.syncon.data.local.dao.BlockEventDao
import com.yu.syncon.data.local.dao.DailyUsageDao
import com.yu.syncon.data.local.dao.UsageIntervalDao
import com.yu.syncon.data.local.dao.RemoteUsageIntervalDao
import com.yu.syncon.data.local.dao.SyncMetadataDao
import com.yu.syncon.data.local.entity.AppConfig
import com.yu.syncon.data.local.entity.AppDailyState
import com.yu.syncon.data.local.entity.AppInfo
import com.yu.syncon.data.local.entity.AppLimitSettings
import com.yu.syncon.data.local.entity.BlockEvent
import com.yu.syncon.data.local.entity.DailyUsage
import com.yu.syncon.data.local.entity.UsageInterval
import com.yu.syncon.data.local.entity.RemoteUsageInterval
import com.yu.syncon.data.local.entity.ConnectedInstallationCache
import com.yu.syncon.data.local.entity.SyncConflict
import com.yu.syncon.data.local.entity.SyncCursor
import com.yu.syncon.data.local.entity.SyncUploadAttempt

@Database(
    entities = [
        AppInfo::class,
        DailyUsage::class,
        AppLimitSettings::class,
        AppDailyState::class,
        BlockEvent::class,
        AppConfig::class,
        UsageInterval::class,
        RemoteUsageInterval::class,
        SyncCursor::class,
        ConnectedInstallationCache::class,
        SyncConflict::class,
        SyncUploadAttempt::class
    ],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appInfoDao(): AppInfoDao
    abstract fun dailyUsageDao(): DailyUsageDao
    abstract fun appLimitSettingsDao(): AppLimitSettingsDao
    abstract fun appDailyStateDao(): AppDailyStateDao
    abstract fun blockEventDao(): BlockEventDao
    abstract fun appConfigDao(): AppConfigDao
    abstract fun usageIntervalDao(): UsageIntervalDao
    abstract fun remoteUsageIntervalDao(): RemoteUsageIntervalDao
    abstract fun syncMetadataDao(): SyncMetadataDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE daily_usage ADD COLUMN durationMillis INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "UPDATE daily_usage SET durationMillis = durationMinutes * 60000"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS usage_interval (
                        recordId TEXT NOT NULL PRIMARY KEY,
                        installationId TEXT NOT NULL,
                        sourcePlatform TEXT NOT NULL,
                        sourceType TEXT NOT NULL,
                        sourceIdentifier TEXT NOT NULL,
                        usageDate TEXT NOT NULL,
                        startTimeUtc INTEGER NOT NULL,
                        endTimeUtc INTEGER NOT NULL,
                        durationMillis INTEGER NOT NULL,
                        timezoneId TEXT NOT NULL,
                        utcOffsetMinutes INTEGER NOT NULL,
                        createdAtUtc INTEGER NOT NULL,
                        updatedAtUtc INTEGER NOT NULL,
                        localRevision INTEGER NOT NULL,
                        serverRevision INTEGER,
                        syncState TEXT NOT NULL,
                        isDeleted INTEGER NOT NULL
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_usage_interval_usageDate ON usage_interval(usageDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_usage_interval_syncState ON usage_interval(syncState)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_usage_interval_installationId_startTimeUtc_endTimeUtc ON usage_interval(installationId, startTimeUtc, endTimeUtc)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_limit_settings ADD COLUMN recordId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_limit_settings ADD COLUMN updatedAtUtc INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_limit_settings ADD COLUMN localRevision INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_limit_settings ADD COLUMN serverRevision INTEGER")
                db.execSQL("ALTER TABLE app_limit_settings ADD COLUMN syncState TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
                db.execSQL("ALTER TABLE app_limit_settings ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE app_limit_settings SET recordId = 'android-app-limit-' || packageName WHERE recordId = ''")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_info ADD COLUMN updatedAtUtc INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_info ADD COLUMN localRevision INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_info ADD COLUMN serverRevision INTEGER")
                db.execSQL("ALTER TABLE app_info ADD COLUMN syncState TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
                db.execSQL("ALTER TABLE app_info ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS remote_usage_interval (
                        recordId TEXT NOT NULL PRIMARY KEY,
                        installationId TEXT NOT NULL,
                        sourcePlatform TEXT NOT NULL,
                        sourceType TEXT NOT NULL,
                        sourceIdentifier TEXT NOT NULL,
                        usageDate TEXT NOT NULL,
                        startTimeUtc INTEGER NOT NULL,
                        endTimeUtc INTEGER NOT NULL,
                        durationMillis INTEGER NOT NULL,
                        timezoneId TEXT NOT NULL,
                        utcOffsetMinutes INTEGER NOT NULL,
                        localRevision INTEGER NOT NULL,
                        serverRevision INTEGER NOT NULL,
                        isDeleted INTEGER NOT NULL
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_remote_usage_interval_usageDate ON remote_usage_interval(usageDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_remote_usage_interval_sourcePlatform_usageDate ON remote_usage_interval(sourcePlatform, usageDate)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_cursor (scope TEXT NOT NULL PRIMARY KEY, serverRevision INTEGER NOT NULL, updatedAtUtc INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS connected_installation_cache (installationId TEXT NOT NULL PRIMARY KEY, platform TEXT NOT NULL, displayName TEXT NOT NULL, clientVersion TEXT NOT NULL, lastSeenAt TEXT NOT NULL, isCurrent INTEGER NOT NULL, cachedAtUtc INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_conflict (recordId TEXT NOT NULL PRIMARY KEY, collection TEXT NOT NULL, localPayload TEXT NOT NULL, serverPayload TEXT NOT NULL, serverRevision INTEGER NOT NULL, detectedAtUtc INTEGER NOT NULL, resolvedAtUtc INTEGER)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_upload_attempt (recordId TEXT NOT NULL PRIMARY KEY, collection TEXT NOT NULL, attemptCount INTEGER NOT NULL, lastAttemptAtUtc INTEGER NOT NULL, nextAttemptAtUtc INTEGER NOT NULL, lastError TEXT)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "syncon.db"
                )
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .addMigrations(MIGRATION_3_4)
                .addMigrations(MIGRATION_4_5)
                .addMigrations(MIGRATION_5_6)
                .addMigrations(MIGRATION_6_7)
                // Safety policy: if sideloading an older build, avoid crash on schema downgrade
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                // For future schema version upgrades (e.g. 1 -> 2), add .addMigrations(MIGRATION_1_2) here
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

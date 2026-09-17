package com.yu.syncon.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.yu.syncon.data.local.dao.AppConfigDao
import com.yu.syncon.data.local.dao.AppDailyStateDao
import com.yu.syncon.data.local.dao.AppInfoDao
import com.yu.syncon.data.local.dao.AppLimitSettingsDao
import com.yu.syncon.data.local.dao.BlockEventDao
import com.yu.syncon.data.local.dao.DailyUsageDao
import com.yu.syncon.data.local.entity.AppConfig
import com.yu.syncon.data.local.entity.AppDailyState
import com.yu.syncon.data.local.entity.AppInfo
import com.yu.syncon.data.local.entity.AppLimitSettings
import com.yu.syncon.data.local.entity.BlockEvent
import com.yu.syncon.data.local.entity.DailyUsage

@Database(
    entities = [
        AppInfo::class,
        DailyUsage::class,
        AppLimitSettings::class,
        AppDailyState::class,
        BlockEvent::class,
        AppConfig::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appInfoDao(): AppInfoDao
    abstract fun dailyUsageDao(): DailyUsageDao
    abstract fun appLimitSettingsDao(): AppLimitSettingsDao
    abstract fun appDailyStateDao(): AppDailyStateDao
    abstract fun blockEventDao(): BlockEventDao
    abstract fun appConfigDao(): AppConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "syncon.db"
                )
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

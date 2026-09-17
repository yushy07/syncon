package com.yu.syncon.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.yu.syncon.data.local.entity.AppDailyState
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDailyStateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(state: AppDailyState)

    @Query("SELECT * FROM app_daily_state WHERE packageName = :packageName AND usageDate = :usageDate LIMIT 1")
    suspend fun getState(packageName: String, usageDate: String): AppDailyState?

    @Query("SELECT * FROM app_daily_state WHERE packageName = :packageName AND usageDate = :usageDate LIMIT 1")
    fun getStateFlow(packageName: String, usageDate: String): Flow<AppDailyState?>

    @Transaction
    suspend fun getOrCreateState(packageName: String, usageDate: String): AppDailyState {
        val existing = getState(packageName, usageDate)
        if (existing != null) return existing

        val newState = AppDailyState(
            packageName = packageName,
            usageDate = usageDate,
            warningShown = false,
            isBlocked = false,
            extraSnoozeMinutesUsedToday = 0
        )
        insertOrUpdate(newState)
        return getState(packageName, usageDate) ?: newState
    }

    @Query("UPDATE app_daily_state SET warningShown = :shown WHERE packageName = :packageName AND usageDate = :usageDate")
    suspend fun setWarningShown(packageName: String, usageDate: String, shown: Boolean)

    @Query("UPDATE app_daily_state SET isBlocked = :blocked WHERE packageName = :packageName AND usageDate = :usageDate")
    suspend fun setIsBlocked(packageName: String, usageDate: String, blocked: Boolean)

    @Transaction
    suspend fun addSnoozeMinutes(packageName: String, usageDate: String, extraMinutes: Int) {
        val state = getOrCreateState(packageName, usageDate)
        insertOrUpdate(
            state.copy(
                extraSnoozeMinutesUsedToday = state.extraSnoozeMinutesUsedToday + extraMinutes,
                isBlocked = false
            )
        )
    }

    @Query("UPDATE app_daily_state SET warningShown = 0, isBlocked = 0, extraSnoozeMinutesUsedToday = 0 WHERE usageDate = :usageDate")
    suspend fun resetDailyFlagsForDate(usageDate: String)

    @Query("UPDATE app_daily_state SET warningShown = 0, isBlocked = 0, extraSnoozeMinutesUsedToday = 0")
    suspend fun resetAllDailyFlags()
}

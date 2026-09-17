package com.yu.syncon

import android.app.Application
import com.yu.syncon.service.worker.DailyResetWorker
import com.yu.syncon.util.NotificationHelper

class SyncOnApp : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
        DailyResetWorker.schedule(this)
    }
}

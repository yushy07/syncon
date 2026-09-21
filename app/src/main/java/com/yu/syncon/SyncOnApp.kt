package com.yu.syncon

import android.app.Application
import com.yu.syncon.data.repository.UsageRepository
import com.yu.syncon.data.remote.CloudSyncRepository
import com.yu.syncon.data.remote.SecureSessionStore
import com.yu.syncon.data.remote.SupabaseClient
import com.yu.syncon.data.remote.RealtimeSyncListener
import com.yu.syncon.service.worker.CloudSyncWorker
import com.yu.syncon.service.worker.DailyResetWorker
import com.yu.syncon.util.NotificationHelper

class SyncOnApp : Application() {

    private val supabaseClient: SupabaseClient by lazy {
        SupabaseClient(SecureSessionStore(this))
    }

    val realtimeSyncListener: RealtimeSyncListener by lazy {
        RealtimeSyncListener(this, supabaseClient)
    }

    val repository: UsageRepository by lazy {
        UsageRepository(this)
    }

    val cloudSyncRepository: CloudSyncRepository by lazy {
        CloudSyncRepository(
            context = this,
            client = supabaseClient,
            usageRepository = repository,
            onSessionChanged = { realtimeSyncListener.restart() }
        )
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
        DailyResetWorker.schedule(this)
        CloudSyncWorker.schedule(this)
        realtimeSyncListener.start()
    }
}

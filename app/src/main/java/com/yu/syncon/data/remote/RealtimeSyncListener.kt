package com.yu.syncon.data.remote

import android.content.Context
import com.yu.syncon.BuildConfig
import com.yu.syncon.service.worker.CloudSyncWorker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Realtime is a wake-up signal only. The worker always performs the same
 * durable cursor pull used by alarms, resume and manual sync.
 */
class RealtimeSyncListener(
    context: Context,
    private val client: SupabaseClient
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .build()
    private val refs = AtomicLong(1)
    private var runner: Job? = null
    private var socket: WebSocket? = null

    fun start() {
        if (runner?.isActive == true) return
        runner = scope.launch {
            var backoff = 2_000L
            while (currentCoroutineContext().isActive) {
                val session = runCatching { client.currentSession() }.getOrNull()
                if (session == null) {
                    delay(10_000L)
                    continue
                }
                val syncContext = runCatching { client.rpc("get_sync_context_v3") as? JSONObject }.getOrNull()
                val topic = syncContext?.optString("topic").orEmpty()
                if (topic.isBlank()) {
                    delay(10_000L)
                    continue
                }
                val connected = runCatching { connectAndWait(topic, session.accessToken) }.isSuccess
                backoff = if (connected) 2_000L else (backoff * 2).coerceAtMost(60_000L)
                delay(backoff)
            }
        }
    }

    fun restart() {
        runner?.cancel()
        socket?.cancel()
        runner = null
        socket = null
        start()
    }

    private suspend fun connectAndWait(topic: String, accessToken: String) {
        val closed = CompletableDeferred<Unit>()
        val wsUrl = BuildConfig.SUPABASE_URL
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") +
            "/realtime/v1/websocket?apikey=${BuildConfig.SUPABASE_PUBLISHABLE_KEY}&vsn=1.0.0"
        val request = Request.Builder().url(wsUrl).build()
        val channelTopic = "realtime:$topic"
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val ref = refs.getAndIncrement().toString()
                val payload = JSONObject()
                    .put("config", JSONObject()
                        .put("broadcast", JSONObject().put("ack", false).put("self", false))
                        .put("presence", JSONObject().put("key", ""))
                        .put("postgres_changes", org.json.JSONArray())
                        .put("private", true))
                    .put("access_token", accessToken)
                webSocket.send(JSONObject()
                    .put("topic", channelTopic)
                    .put("event", "phx_join")
                    .put("payload", payload)
                    .put("ref", ref)
                    .put("join_ref", ref)
                    .toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = runCatching { JSONObject(text) }.getOrNull() ?: return
                if (message.optString("event") != "broadcast") return
                val payload = message.optJSONObject("payload") ?: return
                if (payload.optString("event") == "sync_changed") {
                    CloudSyncWorker.runNow(appContext)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                closed.complete(Unit)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                closed.complete(Unit)
            }
        })
        try {
            closed.await()
        } finally {
            socket?.cancel()
            socket = null
        }
    }
}

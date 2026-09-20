package com.yu.syncon.data.remote

import com.yu.syncon.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL

class SupabaseClient(private val sessionStore: SecureSessionStore) {

    fun hasSession(): Boolean = sessionStore.load() != null

    suspend fun signUp(email: String, password: String, displayName: String): String {
        val response = request(
            path = "/auth/v1/signup",
            method = "POST",
            body = JSONObject().apply {
                put("email", email.trim())
                put("password", password)
                put("data", JSONObject().put("display_name", displayName.trim()))
            }
        ) as JSONObject
        return if (response.optString("access_token").isNotBlank()) {
            saveSession(response)
            "Account created and signed in."
        } else {
            "Account created. Check your email, then sign in."
        }
    }

    suspend fun signIn(email: String, password: String): AuthSession {
        val response = request(
            path = "/auth/v1/token?grant_type=password",
            method = "POST",
            body = JSONObject().put("email", email.trim()).put("password", password)
        ) as JSONObject
        return saveSession(response)
    }

    suspend fun signOut() {
        val session = sessionStore.load()
        try {
            if (session != null) request("/auth/v1/logout", "POST", JSONObject(), session.accessToken)
        } finally {
            sessionStore.clear()
        }
    }

    suspend fun currentSession(): AuthSession? {
        val existing = sessionStore.load() ?: return null
        if (existing.expiresAtUtc > System.currentTimeMillis() + 60_000L) return existing
        return refresh(existing)
    }

    suspend fun rpc(name: String, parameters: JSONObject = JSONObject()): Any? {
        var session = currentSession() ?: error("Sign in to SyncOn first")
        return try {
            request("/rest/v1/rpc/$name", "POST", parameters, session.accessToken)
        } catch (error: BackendException) {
            if (error.statusCode != 401) throw error
            session = refresh(session)
            request("/rest/v1/rpc/$name", "POST", parameters, session.accessToken)
        }
    }

    private suspend fun refresh(session: AuthSession): AuthSession {
        return try {
            val response = request(
                "/auth/v1/token?grant_type=refresh_token",
                "POST",
                JSONObject().put("refresh_token", session.refreshToken)
            ) as JSONObject
            saveSession(response)
        } catch (error: Exception) {
            if (error is BackendException && error.statusCode in listOf(400, 401)) sessionStore.clear()
            throw error
        }
    }

    private fun saveSession(response: JSONObject): AuthSession {
        val session = AuthSession(
            accessToken = response.getString("access_token"),
            refreshToken = response.getString("refresh_token"),
            expiresAtUtc = System.currentTimeMillis() + response.optLong("expires_in", 3600L) * 1000L,
            userId = response.getJSONObject("user").getString("id")
        )
        sessionStore.save(session)
        return session
    }

    private suspend fun request(
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
        accessToken: String? = null
    ): Any? = withContext(Dispatchers.IO) {
        val connection = (URL(BuildConfig.SUPABASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer ${accessToken ?: BuildConfig.SUPABASE_PUBLISHABLE_KEY}")
            setRequestProperty("Content-Type", "application/json")
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
        }
        val status = connection.responseCode
        val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        connection.disconnect()
        val parsed = if (text.isBlank()) null else JSONTokener(text).nextValue()
        if (status !in 200..299) {
            val message = (parsed as? JSONObject)?.let {
                it.optString("message", it.optString("msg", it.optString("error_description", "Backend request failed")))
            } ?: "Backend request failed"
            throw BackendException(status, message)
        }
        parsed
    }
}

class BackendException(val statusCode: Int, override val message: String) : Exception(message)

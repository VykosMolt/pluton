package io.github.vykosmolt.pluton

import android.content.Context
import io.github.vykosmolt.pluton.core.PullRow
import io.github.vykosmolt.pluton.core.PullResult
import io.github.vykosmolt.pluton.core.PushRow
import io.github.vykosmolt.pluton.core.PushRows
import io.github.vykosmolt.pluton.core.SyncApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Account sync over Supabase's REST API (email OTP + the records table from supabase/migrations). The merge rules
// live in the shared core; this file owns transport and the web app's JSON record shapes.

@Serializable
data class SupabaseConfig(val url: String = "", val anonKey: String = "")

@Serializable
data class SupabaseSession(val accessToken: String, val refreshToken: String, val expiresAt: Long, val email: String)

class SyncPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun config(): SupabaseConfig? = prefs.getString("config", null)?.let {
        runCatching { json.decodeFromString<SupabaseConfig>(it) }.getOrNull()
    }?.takeIf { it.url.isNotBlank() && it.anonKey.isNotBlank() }

    fun saveConfig(config: SupabaseConfig) {
        prefs.edit().putString("config", json.encodeToString(config)).apply()
    }

    fun session(): SupabaseSession? = prefs.getString("session", null)?.let {
        runCatching { json.decodeFromString<SupabaseSession>(it) }.getOrNull()
    }

    fun saveSession(session: SupabaseSession) {
        prefs.edit().putString("session", json.encodeToString(session)).apply()
    }

    fun clearSession() {
        prefs.edit().remove("session").apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}

class SyncError(message: String) : Exception(message)

class SupabaseClient(private val prefs: SyncPrefs) : SyncApi {
    private val json = Json { ignoreUnknownKeys = true }

    fun configured(): Boolean = prefs.config() != null
    fun signedIn(): Boolean = prefs.session() != null

    private fun config(): SupabaseConfig = prefs.config() ?: throw SyncError("Set your Supabase URL and anon key first.")

    private fun message(body: String): String = runCatching {
        val o = json.parseToJsonElement(body).jsonObject
        (o["message"] ?: o["error_description"] ?: o["msg"] ?: o["error"])?.jsonPrimitive?.content
    }.getOrNull().orEmpty().ifBlank { "The sync server returned an error." }

    private fun request(
        method: String,
        path: String,
        body: JsonObject? = null,
        session: SupabaseSession? = null,
        timeoutMs: Int = 60_000,
    ): Pair<Int, String> {
        val cfg = config()
        val connection = try {
            URL(cfg.url.trimEnd('/') + path).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw SyncError("That sync server URL doesn't look right.")
        }
        connection.requestMethod = method
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.setRequestProperty("apikey", cfg.anonKey)
        connection.setRequestProperty("Authorization", "Bearer ${session?.accessToken ?: cfg.anonKey}")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Prefer", "return=minimal")
        return try {
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            status to text
        } catch (e: IOException) {
            throw SyncError("Couldn't reach the sync server. Are you offline?")
        } finally {
            connection.disconnect()
        }
    }

    private fun sessionFrom(body: String): SupabaseSession {
        val o = json.parseToJsonElement(body).jsonObject
        val access = o["access_token"]?.jsonPrimitive?.content ?: throw SyncError("The server didn't return a session.")
        val refresh = o["refresh_token"]?.jsonPrimitive?.content ?: throw SyncError("The server didn't return a session.")
        val expiresAt = o["expires_at"]?.jsonPrimitive?.content?.toLongOrNull()?.times(1000)
            ?: (System.currentTimeMillis() + (o["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600) * 1000)
        val email = (o["user"] as? JsonObject)?.get("email")?.jsonPrimitive?.content.orEmpty()
        return SupabaseSession(access, refresh, expiresAt, email)
    }

    private suspend fun refresh(session: SupabaseSession): SupabaseSession {
        val (status, text) = request(
            "POST",
            "/auth/v1/token?grant_type=refresh_token",
            buildJsonObject { put("refresh_token", session.refreshToken) },
        )
        if (status !in 200..299) throw SyncError("Your session expired. Sign in again.")
        return sessionFrom(text).also(prefs::saveSession)
    }

    private suspend fun session(): SupabaseSession {
        val current = prefs.session() ?: throw SyncError("Sign in first.")
        if (current.expiresAt - System.currentTimeMillis() > 120_000) return current
        return refresh(current)
    }

    suspend fun sendCode(email: String) {
        val (status, text) = request(
            "POST",
            "/auth/v1/otp",
            buildJsonObject {
                put("email", email)
                put("create_user", true)
            },
        )
        if (status !in 200..299) throw SyncError(message(text))
    }

    suspend fun verifyCode(email: String, code: String): SupabaseSession {
        val (status, text) = request(
            "POST",
            "/auth/v1/verify",
            buildJsonObject {
                put("type", "email")
                put("email", email)
                put("token", code)
            },
        )
        if (status !in 200..299) throw SyncError(message(text))
        return sessionFrom(text).also(prefs::saveSession)
    }

    suspend fun signOut() {
        runCatching { request("POST", "/auth/v1/logout", buildJsonObject { }, session()) }
        prefs.clearSession()
    }

    suspend fun deleteAccount() {
        val (status, text) = request("POST", "/rest/v1/rpc/delete_account", buildJsonObject { }, session())
        if (status !in 200..299) throw SyncError(message(text))
        prefs.clearSession()
    }

    override suspend fun pull(since: Long): PullResult = withContext(Dispatchers.IO) {
        val session = session()
        val sinceIso = URLEncoder.encode(iso(since), "UTF-8")
        val all = mutableListOf<PullRow>()
        var maxSyncedAt = 0L
        var count = 0
        var offset = 0
        while (true) {
            val path = "/rest/v1/records?select=store,key,data,updated_at,synced_at&lang=eq.fr" +
                "&synced_at=gt.$sinceIso&order=synced_at.asc&limit=1000&offset=$offset"
            val (status, text) = request("GET", path, session = session)
            if (status !in 200..299) throw SyncError(message(text))
            val page = runCatching { json.parseToJsonElement(text).jsonArray }.getOrNull()
                ?: throw SyncError("The sync server returned an unexpected response.")
            all += parsePullResponse(text)
            maxSyncedAt = maxOf(maxSyncedAt, maxSyncedAtOf(text))
            count += page.size
            if (page.size < 1000) break
            offset += 1000
        }
        PullResult(all, maxSyncedAt, count)
    }

    override suspend fun push(rows: List<PushRow>) = withContext(Dispatchers.IO) {
        if (rows.isEmpty()) return@withContext
        val session = session()
        for (chunk in rows.chunked(500)) {
            val body = buildJsonObject {
                put("lang", "fr")
                putJsonArray("batch") { chunk.forEach { add(pushRowJson(it)) } }
            }
            val (status, text) = request("POST", "/rest/v1/rpc/push", body, session)
            if (status !in 200..299) throw SyncError(message(text))
        }
    }
}

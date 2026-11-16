package com.catsblock.weather.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.catsblock.weather.i18n.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal object AccountAuth {

    sealed interface Result {
        data object Success : Result
        data class Failure(val message: String) : Result
    }

    private class Session(
        val username: String,
        val access: String,
        val refresh: String,
        val accessExpiresAt: Long
    )

    private class Reply(val code: Int, val json: JSONObject)

    private const val PREFS_NAME = "catsblock_account"
    private const val KEY_USERNAME = "username"
    private const val KEY_ACCESS = "access"
    private const val KEY_REFRESH = "refresh"
    private const val KEY_EXPIRES = "expires"
    private const val ACCESS_LIFETIME_MS = 900_000L
    private const val REFRESH_MARGIN_MS = 30_000L
    private const val MIN_PASSWORD = 10
    private const val MAX_PASSWORD = 128

    private const val MSG_USERNAME = "Enter a username of 3 to 32 letters, digits or underscores, starting with a letter."
    private const val MSG_PASSWORD = "Use a password of 10 to 128 characters."
    private const val MSG_MISMATCH = "The passwords do not match."
    private const val MSG_TAKEN = "That username is not available."
    private const val MSG_CREDENTIALS = "Incorrect username or password."
    private const val MSG_EXPIRED = "Your session has expired. Log in again."
    private const val MSG_RATE = "Too many attempts. Try again later."
    private const val MSG_UNAVAILABLE = "Account service temporarily unavailable. Please try again later."
    private const val MSG_NETWORK = "Check your connection and try again."
    private const val MSG_GENERIC = "Something went wrong. Try again."

    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val usernamePattern = Regex("^[a-z][a-z0-9_]{2,31}$")
    private val lock = Any()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    @Volatile private var baseUrl = ""
    @Volatile private var appToken = ""
    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var session: Session? = null

    fun configure(context: Context, baseUrl: String, appToken: String) {
        this.baseUrl = baseUrl
        this.appToken = appToken
        if (prefs == null) prefs = openPrefs(context.applicationContext)
        session = readSession()
    }

    fun loggedIn(): Boolean = session != null

    fun currentUsername(): String = session?.username.orEmpty()

    fun accessToken(): String {
        val current = session ?: return ""
        if (isFresh(current)) return current.access
        return synchronized(lock) {
            val latest = session ?: return@synchronized ""
            if (isFresh(latest)) latest.access else refreshLocked(latest)
        }
    }

    suspend fun register(username: String, password: String, confirmation: String): Result {
        val name = username.trim().lowercase()
        if (!usernamePattern.matches(name) || name in reserved) return Result.Failure(tr(MSG_USERNAME))
        if (password.length !in MIN_PASSWORD..MAX_PASSWORD) return Result.Failure(tr(MSG_PASSWORD))
        if (password != confirmation) return Result.Failure(tr(MSG_MISMATCH))
        val payload = JSONObject()
            .put("username", name)
            .put("password", password)
            .put("passwordConfirmation", confirmation)
        return authenticate("/v1/auth/register", payload)
    }

    suspend fun login(username: String, password: String): Result {
        val name = username.trim().lowercase()
        if (name.isEmpty() || password.length !in MIN_PASSWORD..MAX_PASSWORD) return Result.Failure(tr(MSG_CREDENTIALS))
        val payload = JSONObject().put("username", name).put("password", password)
        return authenticate("/v1/auth/login", payload)
    }

    suspend fun logout() {
        val current = session
        clearSession()
        if (current == null) return
        withContext(Dispatchers.IO) {
            try {
                send("/v1/auth/logout", JSONObject(), current.access)
            } catch (error: CancellationException) {
                throw error
            } catch (_: IOException) {
            }
        }
    }

    suspend fun delete(password: String): Result {
        if (password.length !in MIN_PASSWORD..MAX_PASSWORD) return Result.Failure(tr(MSG_CREDENTIALS))
        return withContext(Dispatchers.IO) {
            val token = accessToken()
            if (token.isEmpty()) return@withContext Result.Failure(tr(MSG_EXPIRED))
            try {
                val reply = send("/v1/auth/delete", JSONObject().put("password", password), token)
                if (reply.code == 200) {
                    clearSession()
                    Result.Success
                } else {
                    failure(reply)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: IOException) {
                Result.Failure(tr(MSG_NETWORK))
            }
        }
    }

    private val reserved = setOf("admin", "root", "system", "support", "moderator", "security")

    private suspend fun authenticate(path: String, payload: JSONObject): Result = withContext(Dispatchers.IO) {
        try {
            val reply = send(path, payload, "")
            if (reply.code in 200..201 && store(reply.json)) Result.Success else failure(reply)
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            Result.Failure(tr(MSG_NETWORK))
        }
    }

    private fun send(path: String, payload: JSONObject, bearer: String): Reply {
        if (baseUrl.isEmpty()) throw IOException("Service endpoint is not configured")
        payload.put("installationId", DeviceFingerprint.id)
        val builder = Request.Builder()
            .url(baseUrl + path)
            .header("X-App-Token", appToken)
            .header("X-Device-Id", DeviceFingerprint.id)
            .header("X-Client-Platform", "android")
            .post(payload.toString().toRequestBody(jsonType))
        if (bearer.isNotEmpty()) builder.header("Authorization", "Bearer $bearer")
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = try { JSONObject(text) } catch (_: Exception) { JSONObject() }
            return Reply(response.code, json)
        }
    }

    private fun refreshLocked(current: Session): String {
        return try {
            val reply = send("/v1/auth/refresh", JSONObject().put("refreshToken", current.refresh), "")
            when {
                reply.code == 200 && store(reply.json) -> session?.access.orEmpty()
                reply.code == 401 -> {
                    clearSession()
                    ""
                }
                else -> current.access
            }
        } catch (_: IOException) {
            current.access
        }
    }

    private fun store(json: JSONObject): Boolean {
        val user = json.optJSONObject("user") ?: return false
        val username = user.optString("username")
        val access = json.optString("accessToken")
        val refresh = json.optString("refreshToken")
        if (username.isEmpty() || access.isEmpty() || refresh.isEmpty()) return false
        val created = Session(username, access, refresh, System.currentTimeMillis() + ACCESS_LIFETIME_MS)
        session = created
        prefs?.edit()
            ?.putString(KEY_USERNAME, created.username)
            ?.putString(KEY_ACCESS, created.access)
            ?.putString(KEY_REFRESH, created.refresh)
            ?.putLong(KEY_EXPIRES, created.accessExpiresAt)
            ?.apply()
        return true
    }

    private fun clearSession() {
        session = null
        prefs?.edit()?.clear()?.apply()
    }

    private fun readSession(): Session? {
        val store = prefs ?: return null
        val username = store.getString(KEY_USERNAME, null).orEmpty()
        val access = store.getString(KEY_ACCESS, null).orEmpty()
        val refresh = store.getString(KEY_REFRESH, null).orEmpty()
        if (username.isEmpty() || refresh.isEmpty()) return null
        return Session(username, access, refresh, store.getLong(KEY_EXPIRES, 0L))
    }

    private fun openPrefs(context: Context): SharedPreferences? = try {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        null
    }

    private fun isFresh(value: Session): Boolean =
        value.access.isNotEmpty() && value.accessExpiresAt - System.currentTimeMillis() > REFRESH_MARGIN_MS

    private fun failure(reply: Reply): Result.Failure {
        val error = reply.json.optString("error")
        val key = when {
            error == "invalid_username" -> MSG_USERNAME
            error == "invalid_password" -> MSG_PASSWORD
            error == "password_mismatch" -> MSG_MISMATCH
            error == "account_unavailable" || reply.code == 409 -> MSG_TAKEN
            error == "invalid_credentials" -> MSG_CREDENTIALS
            reply.code == 401 -> MSG_EXPIRED
            reply.code == 429 -> MSG_RATE
            reply.code == 451 || reply.code in 500..599 -> MSG_UNAVAILABLE
            else -> MSG_GENERIC
        }
        return Result.Failure(tr(key))
    }
}

package com.nursecenter.nurse.data

import com.nursecenter.nurse.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String,
    /** Epoch millis when [accessToken] stops being accepted. */
    val expiresAt: Long,
)

class AuthException(message: String) : Exception(message)

/** Minimal client for Supabase Auth (GoTrue) email/password sign-in. */
object SupabaseAuth {
    var session: AuthSession? = null
        private set

    suspend fun signIn(email: String, password: String): AuthSession = withContext(Dispatchers.IO) {
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) {
            throw AuthException("Supabase is not configured.")
        }
        val body = JSONObject().put("email", email).put("password", password).toString()
        val (code, response) = try {
            post("/auth/v1/token?grant_type=password", body, bearer = null)
        } catch (e: IOException) {
            throw AuthException("Can't reach the server. Check your internet connection.")
        }
        val json = runCatching { JSONObject(response) }.getOrElse { JSONObject() }
        if (code !in 200..299) throw AuthException(errorMessage(json, code))

        val signedIn = parseSession(json)
        if (!isActiveCaregiver(signedIn)) {
            runCatching { post("/auth/v1/logout", "{}", bearer = signedIn.accessToken) }
            throw AuthException("This app is for Nurse Center nurses only. Please use a nurse account.")
        }
        signedIn.also {
            session = it
            SessionStore.save(it)
        }
    }

    /**
     * Loads the session saved on this device by an earlier sign-in, so the nurse stays signed in after
     * the app is closed. Call once at startup; the token is refreshed on first use by [validSession].
     */
    fun restore(context: android.content.Context) {
        SessionStore.init(context)
        if (session == null) session = SessionStore.load()
    }

    /** Only accounts whose profile role is `caregiver` (and not deleted) may use the nurse app. */
    private fun isActiveCaregiver(session: AuthSession): Boolean {
        val (code, body) = try {
            request("GET", "/rest/v1/profiles?select=role,deleted_at&id=eq.${session.userId}", null, session.accessToken)
        } catch (e: IOException) {
            throw AuthException("Can't reach the server. Check your internet connection.")
        }
        if (code !in 200..299) throw AuthException("Couldn't verify your account ($code). Please try again.")
        val profile = JSONArray(body).optJSONObject(0) ?: return false
        return profile.optString("role") == "caregiver" && profile.isNull("deleted_at")
    }

    /** Returns the current session, refreshing the access token first if it is about to expire. */
    suspend fun validSession(): AuthSession = refreshLock.withLock {
        val current = session ?: throw AuthException("You are signed out. Please sign in again.")
        if (current.expiresAt - System.currentTimeMillis() > 120_000) return current
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("refresh_token", current.refreshToken).toString()
            val (code, response) = try {
                post("/auth/v1/token?grant_type=refresh_token", body, bearer = null)
            } catch (e: IOException) {
                throw AuthException("Can't reach the server. Check your internet connection.")
            }
            if (code !in 200..299) {
                // Rejected refresh token (signed out elsewhere, password changed, account removed): forget it.
                if (session === current) {
                    session = null
                    SessionStore.clear()
                }
                throw AuthException("Your session has expired. Please sign in again.")
            }
            // Refresh tokens are single-use, so the new one must replace the saved one.
            parseSession(JSONObject(response)).also {
                if (session === current) {
                    session = it
                    SessionStore.save(it)
                }
            }
        }
    }

    private val refreshLock = Mutex()

    private fun parseSession(json: JSONObject): AuthSession {
        val user = json.getJSONObject("user")
        return AuthSession(
            accessToken = json.getString("access_token"),
            refreshToken = json.getString("refresh_token"),
            userId = user.getString("id"),
            email = user.optString("email"),
            expiresAt = System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000,
        )
    }

    suspend fun signOut() {
        val token = session?.accessToken
        session = null
        SessionStore.clear()
        if (token != null) withContext(Dispatchers.IO) {
            runCatching { post("/auth/v1/logout", "{}", bearer = token) }
        }
    }

    private fun post(path: String, body: String, bearer: String?): Pair<Int, String> =
        request("POST", path, body, bearer)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Blocking HTTP call to the Supabase project; returns the status code and response body. */
    internal fun request(
        method: String,
        path: String,
        body: String?,
        bearer: String?,
        headers: Map<String, String> = emptyMap(),
    ): Pair<Int, String> {
        val builder = Request.Builder()
            .url(BuildConfig.SUPABASE_URL.trimEnd('/') + path)
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer ${bearer ?: BuildConfig.SUPABASE_ANON_KEY}")
            .method(method, body?.toRequestBody("application/json".toMediaType()))
        headers.forEach { (name, value) -> builder.header(name, value) }
        http.newCall(builder.build()).execute().use { response ->
            return response.code to response.body?.string().orEmpty()
        }
    }

    /** Blocking upload of raw bytes (e.g. a file to Supabase Storage); returns the status code and response body. */
    internal fun upload(path: String, bytes: ByteArray, contentType: String, bearer: String, headers: Map<String, String> = emptyMap()): Pair<Int, String> {
        val builder = Request.Builder()
            .url(BuildConfig.SUPABASE_URL.trimEnd('/') + path)
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $bearer")
            .post(bytes.toRequestBody(contentType.toMediaType()))
        headers.forEach { (name, value) -> builder.header(name, value) }
        uploadHttp.newCall(builder.build()).execute().use { response ->
            return response.code to response.body?.string().orEmpty()
        }
    }

    // Longer write timeout than [http] for multi-megabyte documents on mobile data.
    private val uploadHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun errorMessage(json: JSONObject, code: Int): String {
        val raw = json.optString("msg").ifBlank { json.optString("error_description") }.ifBlank { json.optString("message") }
        return when {
            raw.contains("Invalid login credentials", ignoreCase = true) -> "Incorrect email or password."
            raw.contains("Email not confirmed", ignoreCase = true) -> "Please confirm your email before signing in."
            raw.isNotBlank() -> raw
            else -> "Sign in failed ($code). Please try again."
        }
    }
}

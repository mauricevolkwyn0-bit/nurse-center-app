package com.nursecenter.nurse.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * The nurse's Online / Offline status, stored in `caregiver_profiles.is_available` like the website's toggle.
 * Offline nurses are left out of broadcast requests (`get_nearby_online_caregiver_ids`) and shown as
 * unavailable to clients; the app also stops alerting them to requests.
 */
object NurseStatus {
    private val _online = MutableStateFlow<Boolean?>(null)
    /** Null until loaded, or if it couldn't be read. */
    val online: StateFlow<Boolean?> = _online

    suspend fun load() = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val row = HomeRepository.getArray(session, "/rest/v1/caregiver_profiles?select=is_available&id=eq.${session.userId}").optJSONObject(0)
        _online.value = row?.takeUnless { it.isNull("is_available") }?.getBoolean("is_available")
    }

    /** Called when the status changes elsewhere (e.g. the website toggle), as reported by Realtime. */
    internal fun onRemoteChange(online: Boolean) {
        _online.value = online
    }

    fun clear() {
        _online.value = null
    }

    suspend fun set(online: Boolean) = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val me = session.userId
        val body = JSONObject().put("is_available", online).toString()
        val updated = patch(session, "/rest/v1/caregiver_profiles?id=eq.$me", body)
        if (updated.length() == 0) throw AuthException("Couldn't change your status. Please try again.")
        _online.value = online
        // The online-nurse lookup prefers the live-location row's flag when one exists, so keep it in step.
        // Only updates an existing row: creating one needs a location.
        patch(session, "/rest/v1/caregiver_locations?caregiver_id=eq.$me", body)
    }

    private fun patch(session: AuthSession, path: String, body: String): JSONArray {
        val (code, response) = try {
            SupabaseAuth.request("PATCH", path, body, session.accessToken, mapOf("Prefer" to "return=representation"))
        } catch (e: IOException) {
            throw AuthException("Can't reach the server. Check your internet connection.")
        }
        if (code == 401) throw AuthException("Your session has expired. Please sign in again.")
        if (code !in 200..299) throw AuthException("Couldn't change your status ($code). Please try again.")
        return JSONArray(response.ifBlank { "[]" })
    }
}

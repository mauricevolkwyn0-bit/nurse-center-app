package com.nursecenter.nurse.data

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class AppNotification(
    val id: String,
    /** e.g. `booking_cancelled`, `booking_confirmed`, `agency_invite`. */
    val type: String,
    val title: String,
    val body: String?,
    val createdAt: ZonedDateTime,
    val read: Boolean,
)

/**
 * The nurse's notifications in the Supabase `notifications` table (written by booking triggers and the
 * website), matching the web nurse Notifications page.
 */
object NotificationsRepository {
    private const val TAG = "NotificationsRepository"

    private val _hasUnread = MutableStateFlow(false)
    /** Drives the dot on the bell. */
    val hasUnread: StateFlow<Boolean> = _hasUnread

    suspend fun load(): List<AppNotification> = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val zone = ZoneId.systemDefault()
        val rows = HomeRepository.getArray(
            session,
            "/rest/v1/notifications?select=id,type,title,body,read_at,created_at&user_id=eq.${session.userId}&order=created_at.desc&limit=60",
        )
        (0 until rows.length()).map { rows.getJSONObject(it) }.map { json ->
            AppNotification(
                id = json.getString("id"),
                type = json.optString("type"),
                title = json.optString("title"),
                body = json.optStringOrNull("body"),
                createdAt = OffsetDateTime.parse(json.getString("created_at")).atZoneSameInstant(zone),
                read = !json.isNull("read_at"),
            )
        }.also { list -> _hasUnread.value = list.any { !it.read } }
    }

    /** Re-checks for unread notifications. Best effort: keeps the last value if the check fails. */
    suspend fun refreshUnread() = withContext(Dispatchers.IO) {
        try {
            val session = SupabaseAuth.validSession()
            _hasUnread.value = HomeRepository.getArray(
                session, "/rest/v1/notifications?select=id&user_id=eq.${session.userId}&read_at=is.null&limit=1",
            ).length() > 0
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't check notifications: ${e.message}")
        }
    }

    /** Marks everything read, as the nurse has now seen the list. */
    suspend fun markAllRead() = withContext(Dispatchers.IO) {
        _hasUnread.value = false
        runCatching {
            val session = SupabaseAuth.validSession()
            val body = JSONObject().put("read_at", OffsetDateTime.now().toString()).toString()
            val (code, response) = SupabaseAuth.request(
                "PATCH", "/rest/v1/notifications?user_id=eq.${session.userId}&read_at=is.null", body, session.accessToken,
            )
            if (code !in 200..299) Log.w(TAG, "Couldn't mark notifications read ($code): $response")
        }.onFailure { if (it !is IOException) Log.w(TAG, "Couldn't mark notifications read: ${it.message}") }
    }

    fun clear() {
        _hasUnread.value = false
    }
}

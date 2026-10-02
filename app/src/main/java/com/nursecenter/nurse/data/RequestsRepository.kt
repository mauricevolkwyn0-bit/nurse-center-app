package com.nursecenter.nurse.data

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.ZoneId
import java.time.ZonedDateTime

data class RequestsData(
    /** Open requests waiting for this nurse, newest first. */
    val pending: List<BookingSummary>,
    /** This nurse's past and upcoming bookings, most recently updated first. */
    val recent: List<BookingSummary>,
)

/** Loads and responds to client requests in the Supabase `bookings` and `job_alerts` tables. */
object RequestsRepository {
    private const val FIELDS = HomeRepository.BOOKING_FIELDS

    private val _hasPending = MutableStateFlow(false)
    /** Whether open requests are waiting for this nurse, as of the last [load]; drives the dot on the Requests tab. */
    val hasPending: StateFlow<Boolean> = _hasPending

    /** Re-checks for open requests. Best effort: keeps the last value if the check fails. */
    suspend fun refreshPending() {
        try {
            load()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("RequestsRepository", "Couldn't check for requests: ${e.message}")
        }
    }

    fun clearPending() {
        _hasPending.value = false
    }

    suspend fun load(): RequestsData = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val me = session.userId
        val zone = ZoneId.systemDefault()
        val mine = "caregiver_id=eq.$me&deleted_at=is.null"

        coroutineScope {
            val assigned = async {
                HomeRepository.getArray(session, "/rest/v1/bookings?select=$FIELDS&$mine&status=eq.pending&order=created_at.desc")
            }
            // Broadcast requests; optional so a missing table or policy never breaks the screen.
            val alerted = async {
                runCatching {
                    HomeRepository.getArray(
                        session,
                        "/rest/v1/job_alerts?select=created_at,booking:bookings!booking_id($FIELDS,deleted_at)" +
                            "&caregiver_id=eq.$me&order=created_at.desc&limit=20",
                    )
                }.getOrNull()
            }
            val recent = async {
                HomeRepository.getArray(
                    session,
                    "/rest/v1/bookings?select=$FIELDS&$mine&status=neq.pending&order=updated_at.desc&limit=10",
                )
            }

            val now = ZonedDateTime.now(zone)
            val pending = objects(assigned.await()).map { HomeRepository.toBooking(it, zone) } +
                objects(alerted.await()).mapNotNull { HomeRepository.alertBooking(it, zone) }
            RequestsData(
                // Requests past their response window are no longer shown.
                pending = pending.filter { it.expiresAt.isAfter(now) }
                    .distinctBy { it.id }
                    .sortedByDescending { it.requestedAt },
                recent = objects(recent.await()).map { HomeRepository.toBooking(it, zone) },
            ).also { _hasPending.value = it.pending.isNotEmpty() }
        }
    }

    /** Takes the request. Fails if another nurse got there first or it was withdrawn. */
    suspend fun accept(booking: BookingSummary) = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val body = JSONObject().put("status", "accepted").put("caregiver_id", session.userId)
        if (!booking.expiresAt.isAfter(ZonedDateTime.now())) throw AuthException("This request has expired.")
        val updated = patch(session, "/rest/v1/bookings?id=eq.${booking.id}&status=eq.pending", body)
        if (updated.length() == 0) throw AuthException("This request is no longer available.")
    }

    /**
     * Turns the request down. A booking the client addressed to this nurse is cancelled with a reason;
     * a broadcast request just has this nurse's job alert removed so it stays open for others.
     */
    suspend fun decline(booking: BookingSummary) = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val me = session.userId
        if (booking.caregiverId == me) {
            val body = JSONObject()
                .put("status", "cancelled")
                .put("cancelled_by", me)
                .put("cancel_reason", "Declined by nurse")
            val updated = patch(session, "/rest/v1/bookings?id=eq.${booking.id}&caregiver_id=eq.$me&status=eq.pending", body)
            if (updated.length() == 0) throw AuthException("This request is no longer available.")
        } else {
            send(session, "DELETE", "/rest/v1/job_alerts?caregiver_id=eq.$me&booking_id=eq.${booking.id}", null)
        }
    }

    private fun patch(session: AuthSession, path: String, body: JSONObject): JSONArray =
        JSONArray(send(session, "PATCH", path, body.toString()))

    private fun send(session: AuthSession, method: String, path: String, body: String?): String {
        val (code, response) = try {
            SupabaseAuth.request(method, path, body, session.accessToken, mapOf("Prefer" to "return=representation"))
        } catch (e: IOException) {
            throw AuthException("Can't reach the server. Check your internet connection.")
        }
        if (code == 401) throw AuthException("Your session has expired. Please sign in again.")
        if (code == 403) throw AuthException("You don't have permission to respond to this request.")
        if (code !in 200..299) {
            android.util.Log.w("RequestsRepository", "$method $path failed ($code): $response")
            val error = runCatching { JSONObject(response) }.getOrNull()
            // 23P01: the no_caregiver_double_booking constraint (migration 011).
            if (error?.optString("code") == "23P01") {
                throw AuthException("This request overlaps a booking you've already accepted.")
            }
            throw AuthException("Couldn't update the request ($code). Please try again.")
        }
        return response.ifBlank { "[]" }
    }

    private fun objects(rows: JSONArray?): List<JSONObject> =
        if (rows == null) emptyList() else (0 until rows.length()).map { rows.getJSONObject(it) }
}

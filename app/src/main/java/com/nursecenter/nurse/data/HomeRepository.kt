package com.nursecenter.nurse.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class BookingSummary(
    val id: String,
    val serviceName: String,
    val patientName: String?,
    val scheduledAt: ZonedDateTime,
    val durationHours: Double,
    val location: String?,
    val notes: String?,
    val payout: Double,
    val status: String,
    val createdAt: ZonedDateTime,
    /** Null when the request was broadcast via job_alerts and nobody has taken it yet. */
    val caregiverId: String?,
    /** When the request reached this nurse: the job alert's time for broadcasts, otherwise the booking's. */
    val requestedAt: ZonedDateTime = createdAt,
) {
    /** When this nurse's window to respond closes. */
    val expiresAt: ZonedDateTime get() = requestedAt.plus(RESPONSE_WINDOW)
}

/** How long a nurse has to accept or decline a new request. */
val RESPONSE_WINDOW: Duration = Duration.ofMinutes(5)

data class HomeData(
    val fullName: String?,
    val newRequest: BookingSummary?,
    val monthEarnings: Double,
    val completedShifts: Int,
    val nextBooking: BookingSummary?,
)

/** Loads the nurse's Home screen data from the Supabase `profiles` and `bookings` tables. */
object HomeRepository {
    internal const val BOOKING_FIELDS =
        "id,status,caregiver_id,scheduled_at,created_at,duration_hours,location_name,notes,total_price,caregiver_payout," +
            "service:services(name),patient:profiles!patient_id(full_name)"

    suspend fun load(): HomeData = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val me = session.userId
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val monthStart = now.withDayOfMonth(1).toLocalDate().atStartOfDay(zone)
        val mine = "caregiver_id=eq.$me&deleted_at=is.null"

        coroutineScope {
            val profile = async { getArray(session, "/rest/v1/profiles?select=full_name&id=eq.$me") }
            val pending = async {
                getArray(session, "/rest/v1/bookings?select=$BOOKING_FIELDS&$mine&status=eq.pending&order=created_at.desc&limit=1")
            }
            // Open requests broadcast to this nurse via job_alerts; optional, so a failure here never breaks Home.
            val alerted = async {
                runCatching {
                    getArray(
                        session,
                        "/rest/v1/job_alerts?select=created_at,booking:bookings!booking_id($BOOKING_FIELDS,deleted_at)" +
                            "&caregiver_id=eq.$me&order=created_at.desc&limit=10",
                    )
                }.getOrNull()
            }
            val completed = async {
                getArray(
                    session,
                    "/rest/v1/bookings?select=total_price,caregiver_payout&$mine&status=eq.completed" +
                        "&scheduled_at=gte.${iso(monthStart)}&scheduled_at=lt.${iso(monthStart.plusMonths(1))}",
                )
            }
            // Only bookings the client has paid for: payment is held on their card ("authorized") or taken ("paid").
            val next = async {
                getArray(
                    session,
                    "/rest/v1/bookings?select=$BOOKING_FIELDS&$mine&status=eq.accepted&payment_status=in.(authorized,paid)" +
                        "&scheduled_at=gte.${iso(now)}&order=scheduled_at.asc&limit=1",
                )
            }

            val completedRows = completed.await()
            val assigned = pending.await().optJSONObject(0)?.let { toBooking(it, zone) }
            val alertedBooking = alerted.await()?.let { rows ->
                (0 until rows.length())
                    .mapNotNull { alertBooking(rows.getJSONObject(it), zone) }
                    .firstOrNull { it.expiresAt.isAfter(now) }
            }
            HomeData(
                fullName = profile.await().optJSONObject(0)?.optStringOrNull("full_name"),
                newRequest = assigned?.takeIf { it.expiresAt.isAfter(now) } ?: alertedBooking,
                monthEarnings = (0 until completedRows.length()).sumOf { payoutOf(completedRows.getJSONObject(it)) },
                completedShifts = completedRows.length(),
                nextBooking = next.await().optJSONObject(0)?.let { toBooking(it, zone) },
            )
        }
    }

    internal fun getArray(session: AuthSession, path: String): JSONArray {
        val (code, body) = try {
            SupabaseAuth.request("GET", path, null, session.accessToken)
        } catch (e: IOException) {
            throw AuthException("Can't reach the server. Check your internet connection.")
        }
        if (code == 401) throw AuthException("Your session has expired. Please sign in again.")
        if (code !in 200..299) throw AuthException("Couldn't load your data ($code).")
        return JSONArray(body)
    }

    internal fun toBooking(json: JSONObject, zone: ZoneId) = BookingSummary(
        id = json.getString("id"),
        serviceName = json.optJSONObject("service")?.optStringOrNull("name") ?: "Care visit",
        patientName = json.optJSONObject("patient")?.optStringOrNull("full_name"),
        scheduledAt = OffsetDateTime.parse(json.optStringOrNull("scheduled_at") ?: json.getString("created_at")).atZoneSameInstant(zone),
        durationHours = json.optDouble("duration_hours", 0.0).takeUnless { it.isNaN() } ?: 0.0,
        location = json.optStringOrNull("location_name"),
        notes = json.optStringOrNull("notes"),
        payout = payoutOf(json),
        status = json.optString("status"),
        createdAt = OffsetDateTime.parse(json.getString("created_at")).atZoneSameInstant(zone),
        caregiverId = json.optStringOrNull("caregiver_id"),
    )

    /** The still-open booking behind a `job_alerts` row, timed from when the alert was sent. */
    internal fun alertBooking(row: JSONObject, zone: ZoneId): BookingSummary? {
        val booking = row.optJSONObject("booking") ?: return null
        if (booking.optString("status") != "pending" || !booking.isNull("deleted_at")) return null
        val summary = toBooking(booking, zone)
        val sent = row.optStringOrNull("created_at") ?: return summary
        return summary.copy(requestedAt = OffsetDateTime.parse(sent).atZoneSameInstant(zone))
    }

    private fun payoutOf(json: JSONObject): Double {
        val payout = json.optDouble("caregiver_payout")
        return if (!payout.isNaN()) payout else json.optDouble("total_price").takeUnless { it.isNaN() } ?: 0.0
    }

    internal fun iso(time: ZonedDateTime): String =
        URLEncoder.encode(time.toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), "UTF-8")

}

internal fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

package com.nursecenter.nurse.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

data class ScheduleWeek(
    /** Confirmed shifts starting in the week, earliest first. */
    val bookings: List<BookingSummary>,
    /** The nurse's live availability from `caregiver_profiles`; null if it couldn't be read. */
    val available: Boolean?,
)

/** Loads the nurse's calendar from the Supabase `bookings` and `caregiver_profiles` tables. */
object ScheduleRepository {
    private const val SCHEDULED_STATUSES = "accepted,in_progress,completed"

    /** @param start the first day of the week to load (7 days from its start of day, device time zone). */
    suspend fun loadWeek(start: LocalDate): ScheduleWeek = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val me = session.userId
        val zone = ZoneId.systemDefault()
        val from = start.atStartOfDay(zone)
        val to = start.plusDays(7).atStartOfDay(zone)

        coroutineScope {
            val bookings = async {
                HomeRepository.getArray(
                    session,
                    "/rest/v1/bookings?select=${HomeRepository.BOOKING_FIELDS}" +
                        "&caregiver_id=eq.$me&deleted_at=is.null&status=in.($SCHEDULED_STATUSES)" +
                        "&scheduled_at=gte.${HomeRepository.iso(from)}&scheduled_at=lt.${HomeRepository.iso(to)}" +
                        "&order=scheduled_at.asc",
                )
            }
            // Optional, so a failure here never hides the bookings.
            val available = async {
                runCatching {
                    HomeRepository.getArray(session, "/rest/v1/caregiver_profiles?select=is_available&id=eq.$me")
                        .optJSONObject(0)?.takeUnless { it.isNull("is_available") }?.getBoolean("is_available")
                }.getOrNull()
            }

            val rows = bookings.await()
            ScheduleWeek(
                bookings = (0 until rows.length()).map { HomeRepository.toBooking(rows.getJSONObject(it), zone) },
                available = available.await(),
            )
        }
    }
}

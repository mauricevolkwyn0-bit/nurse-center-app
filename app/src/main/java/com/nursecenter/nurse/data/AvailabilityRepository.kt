package com.nursecenter.nurse.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class Shift(val label: String, val start: LocalTime, val length: Duration) {
    Morning("Morning", LocalTime.of(6, 0), Duration.ofHours(6)),
    Afternoon("Afternoon", LocalTime.of(12, 0), Duration.ofHours(6)),
    Night("Night", LocalTime.of(18, 0), Duration.ofHours(12)),
    FullDay("Full day", LocalTime.of(8, 0), Duration.ofHours(12));

    /** e.g. "06:00–12:00". */
    val hours: String get() = "${start.format(HHMM)}–${start.plus(length).format(HHMM)}"
}

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

data class WeeklyAvailability(val days: Set<DayOfWeek>, val shift: Shift)

/**
 * The nurse's weekly availability, stored as one recurring row in `availability_slots`
 * (first shift as `start_at`/`end_at`, days as an iCal RRULE such as `FREQ=WEEKLY;BYDAY=MO,WE,FR`).
 */
object AvailabilityRepository {
    private val CODES = mapOf(
        DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE", DayOfWeek.THURSDAY to "TH",
        DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA", DayOfWeek.SUNDAY to "SU",
    )

    /** The saved weekly availability, or null if the nurse hasn't set one. */
    suspend fun load(): WeeklyAvailability? = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val row = HomeRepository.getArray(
            session,
            "/rest/v1/availability_slots?select=start_at,end_at,recurrence_rule" +
                "&caregiver_id=eq.${session.userId}&is_recurring=eq.true&order=start_at.desc&limit=1",
        ).optJSONObject(0) ?: return@withContext null

        val zone = ZoneId.systemDefault()
        val start = OffsetDateTime.parse(row.getString("start_at")).atZoneSameInstant(zone)
        val end = OffsetDateTime.parse(row.getString("end_at")).atZoneSameInstant(zone)
        val byDay = row.optStringOrNull("recurrence_rule")?.substringAfter("BYDAY=", "")?.substringBefore(';').orEmpty()
        val days = byDay.split(',').mapNotNull { code -> CODES.entries.firstOrNull { it.value == code }?.key }.toSet()
        val shift = Shift.entries.firstOrNull { it.start == start.toLocalTime() && it.length == Duration.between(start, end) } ?: Shift.FullDay
        WeeklyAvailability(days.ifEmpty { setOf(start.dayOfWeek) }, shift)
    }

    /** Replaces the nurse's weekly availability. The new row is written before the old ones are removed. */
    suspend fun save(availability: WeeklyAvailability) = withContext(Dispatchers.IO) {
        require(availability.days.isNotEmpty())
        val session = SupabaseAuth.validSession()
        val me = session.userId
        val previous = HomeRepository.getArray(session, "/rest/v1/availability_slots?select=id&caregiver_id=eq.$me&is_recurring=eq.true")

        val zone = ZoneId.systemDefault()
        val firstDay = generateSequence(LocalDate.now(zone)) { it.plusDays(1) }.first { it.dayOfWeek in availability.days }
        val start = firstDay.atTime(availability.shift.start).atZone(zone)
        val byDay = DayOfWeek.entries.filter { it in availability.days }.joinToString(",") { CODES.getValue(it) }
        val body = JSONObject()
            .put("caregiver_id", me)
            .put("start_at", start.toOffsetDateTime().toString())
            .put("end_at", start.plus(availability.shift.length).toOffsetDateTime().toString())
            .put("is_recurring", true)
            .put("recurrence_rule", "FREQ=WEEKLY;BYDAY=$byDay")
        send(session, "POST", "/rest/v1/availability_slots", body.toString())

        val oldIds = (0 until previous.length()).map { previous.getJSONObject(it).getString("id") }
        if (oldIds.isNotEmpty()) send(session, "DELETE", "/rest/v1/availability_slots?id=in.(${oldIds.joinToString(",")})", null)
    }

    private fun send(session: AuthSession, method: String, path: String, body: String?): JSONArray {
        val (code, response) = try {
            SupabaseAuth.request(method, path, body, session.accessToken, mapOf("Prefer" to "return=representation"))
        } catch (e: IOException) {
            throw AuthException("Can't reach the server. Check your internet connection.")
        }
        if (code == 401) throw AuthException("Your session has expired. Please sign in again.")
        if (code !in 200..299) throw AuthException("Couldn't save your availability ($code). Please try again.")
        return JSONArray(response.ifBlank { "[]" })
    }
}

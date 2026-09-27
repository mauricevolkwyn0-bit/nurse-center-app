package com.nursecenter.nurse.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class Review(
    val id: String,
    val clientName: String,
    val serviceName: String?,
    val rating: Int,
    val comment: String?,
    val createdAt: ZonedDateTime,
)

data class ReviewsData(
    val average: Double,
    val total: Int,
    /** Newest first. */
    val reviews: List<Review>,
)

/** Loads the nurse's client reviews from `reviews`, with the rating summary kept on `caregiver_profiles`. */
object ReviewsRepository {
    suspend fun load(): ReviewsData = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val me = session.userId
        val zone = ZoneId.systemDefault()

        coroutineScope {
            val rows = async {
                HomeRepository.getArray(
                    session,
                    "/rest/v1/reviews?select=id,rating,comment,created_at,patient:profiles!patient_id(full_name)," +
                        "booking:bookings!booking_id(service:services(name))" +
                        "&caregiver_id=eq.$me&deleted_at=is.null&order=created_at.desc&limit=50",
                )
            }
            // Optional: falls back to working the summary out from the loaded reviews.
            val summary = async {
                runCatching {
                    HomeRepository.getArray(session, "/rest/v1/caregiver_profiles?select=rating,review_count&id=eq.$me").optJSONObject(0)
                }.getOrNull()
            }

            val array = rows.await()
            val reviews = (0 until array.length()).map { i ->
                val json = array.getJSONObject(i)
                Review(
                    id = json.getString("id"),
                    clientName = json.optJSONObject("patient")?.optStringOrNull("full_name") ?: "Client",
                    serviceName = json.optJSONObject("booking")?.optJSONObject("service")?.optStringOrNull("name"),
                    rating = json.optInt("rating").coerceIn(1, 5),
                    comment = json.optStringOrNull("comment"),
                    createdAt = OffsetDateTime.parse(json.getString("created_at")).atZoneSameInstant(zone),
                )
            }
            val s = summary.await()
            ReviewsData(
                average = s?.optDouble("rating")?.takeUnless { it.isNaN() || reviews.isEmpty() && it == 0.0 }
                    ?: reviews.map { it.rating }.average().takeUnless { it.isNaN() } ?: 0.0,
                total = s?.takeUnless { it.isNull("review_count") }?.optInt("review_count") ?: reviews.size,
                reviews = reviews,
            )
        }
    }
}

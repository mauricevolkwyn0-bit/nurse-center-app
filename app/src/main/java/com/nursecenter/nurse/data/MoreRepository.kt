package com.nursecenter.nurse.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class AccountSummary(
    val fullName: String?,
    /** Public URL of the nurse's profile photo, if they uploaded one. */
    val avatarUrl: String?,
    /** The nurse's specialty, or their qualification when no specialty is set. */
    val title: String?,
    /** `unverified`, `pending`, `verified` or `rejected`; null if the caregiver profile couldn't be read. */
    val verificationStatus: String?,
    val documentsUploaded: Int?,
    val documentsVerified: Int?,
    /** Payouts from completed bookings, as on the web wallet. */
    val earned: Double?,
    /** Payouts from accepted and in-progress bookings. */
    val pending: Double?,
    val rating: Double?,
    val reviewCount: Int?,
)

/** Loads the More screen's account summary from `profiles`, `caregiver_profiles`, `caregiver_documents` and `bookings`. */
object MoreRepository {
    private val QUALIFICATIONS = mapOf(
        "rn" to "Registered Nurse", "en" to "Enrolled Nurse", "pt" to "Physiotherapist",
        "cg" to "Caregiver", "na" to "Nursing Assistant", "hbc" to "Home-Based Care Nurse",
    )

    suspend fun load(): AccountSummary = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val me = session.userId

        coroutineScope {
            val profile = async { HomeRepository.getArray(session, "/rest/v1/profiles?select=full_name,avatar_url&id=eq.$me") }
            // The rest are optional, so one missing table or policy only blanks its own line.
            val caregiver = async {
                runCatching {
                    HomeRepository.getArray(
                        session,
                        "/rest/v1/caregiver_profiles?select=qualification_type,specialty,verification_status,rating,review_count&id=eq.$me",
                    ).optJSONObject(0)
                }.getOrNull()
            }
            val documents = async {
                runCatching {
                    HomeRepository.getArray(
                        session, "/rest/v1/caregiver_documents?select=verified&caregiver_id=eq.$me&type=neq.profile_photo",
                    )
                }.getOrNull()
            }
            val bookings = async {
                runCatching {
                    HomeRepository.getArray(
                        session,
                        "/rest/v1/bookings?select=status,caregiver_payout&caregiver_id=eq.$me&deleted_at=is.null" +
                            "&status=in.(accepted,in_progress,completed)",
                    )
                }.getOrNull()
            }

            val cg = caregiver.await()
            val docs = documents.await()?.let(::objects)
            val jobs = bookings.await()?.let(::objects)
            val row = profile.await().optJSONObject(0)
            AccountSummary(
                fullName = row?.optStringOrNull("full_name"),
                avatarUrl = row?.optStringOrNull("avatar_url"),
                title = cg?.optStringOrNull("specialty") ?: cg?.optStringOrNull("qualification_type")?.let { QUALIFICATIONS[it] },
                verificationStatus = cg?.optStringOrNull("verification_status"),
                documentsUploaded = docs?.size,
                documentsVerified = docs?.count { it.optBoolean("verified") },
                earned = jobs?.filter { it.optString("status") == "completed" }?.sumOf(::payout),
                pending = jobs?.filter { it.optString("status") != "completed" }?.sumOf(::payout),
                rating = cg?.optDouble("rating")?.takeUnless { it.isNaN() },
                reviewCount = cg?.takeUnless { it.isNull("review_count") }?.optInt("review_count"),
            )
        }
    }

    private fun payout(json: JSONObject): Double = json.optDouble("caregiver_payout").takeUnless { it.isNaN() } ?: 0.0

    private fun objects(rows: JSONArray): List<JSONObject> = (0 until rows.length()).map { rows.getJSONObject(it) }
}

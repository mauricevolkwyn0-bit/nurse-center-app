package com.nursecenter.nurse.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.OffsetDateTime

data class NurseProfile(
    val fullName: String,
    /** Public URL of the nurse's profile photo, if they uploaded one. */
    val avatarUrl: String?,
    val email: String,
    val phone: String?,
    /** Qualification label, e.g. "Registered Nurse". */
    val qualification: String?,
    val specialty: String?,
    val sancNumber: String?,
    val yearsExperience: Int?,
    val bio: String?,
    /** The home address / service area saved on the website. */
    val serviceArea: String?,
)

/**
 * Reads and saves the nurse's profile across `profiles`, `profiles_private`, `caregiver_profiles`
 * and the auth user's metadata, matching the web nurse profile page.
 */
object ProfileRepository {
    private val QUALIFICATIONS = mapOf(
        "rn" to "Registered Nurse", "en" to "Enrolled Nurse", "pt" to "Physiotherapist",
        "cg" to "Caregiver Certificate", "na" to "Nursing Assistant", "hbc" to "Home-Based Care", "other" to "Other",
    )

    suspend fun load(): NurseProfile = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val me = session.userId

        coroutineScope {
            val profile = async { HomeRepository.getArray(session, "/rest/v1/profiles?select=full_name,avatar_url&id=eq.$me") }
            val caregiver = async {
                HomeRepository.getArray(
                    session,
                    "/rest/v1/caregiver_profiles?select=qualification_type,specialty,sanc_number,years_experience,bio&id=eq.$me",
                )
            }
            // Optional extras: a failure only leaves their field blank.
            val private = async {
                runCatching { HomeRepository.getArray(session, "/rest/v1/profiles_private?select=phone&id=eq.$me").optJSONObject(0) }.getOrNull()
            }
            val user = async {
                runCatching {
                    val (code, body) = SupabaseAuth.request("GET", "/auth/v1/user", null, session.accessToken)
                    if (code in 200..299) JSONObject(body) else null
                }.getOrNull()
            }

            val p = profile.await().optJSONObject(0)
            val c = caregiver.await().optJSONObject(0)
            val u = user.await()
            NurseProfile(
                fullName = p?.optStringOrNull("full_name").orEmpty(),
                avatarUrl = p?.optStringOrNull("avatar_url"),
                email = u?.optStringOrNull("email") ?: session.email,
                phone = private.await()?.optStringOrNull("phone"),
                qualification = c?.optStringOrNull("qualification_type")?.let { QUALIFICATIONS[it] ?: it },
                specialty = c?.optStringOrNull("specialty"),
                sancNumber = c?.optStringOrNull("sanc_number"),
                yearsExperience = c?.takeUnless { it.isNull("years_experience") }?.optInt("years_experience"),
                bio = c?.optStringOrNull("bio"),
                serviceArea = u?.optJSONObject("user_metadata")?.optStringOrNull("address_name"),
            )
        }
    }

    /** Saves the fields the app lets the nurse edit. */
    suspend fun save(fullName: String, yearsExperience: Int?, bio: String?) = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val me = session.userId
        patch(session, "/rest/v1/profiles?id=eq.$me", JSONObject().put("full_name", fullName))
        patch(
            session, "/rest/v1/caregiver_profiles?id=eq.$me",
            JSONObject()
                .put("years_experience", yearsExperience ?: JSONObject.NULL)
                .put("bio", bio ?: JSONObject.NULL)
                .put("updated_at", OffsetDateTime.now().toString()),
        )
    }

    private fun patch(session: AuthSession, path: String, body: JSONObject) {
        val (code, response) = try {
            SupabaseAuth.request("PATCH", path, body.toString(), session.accessToken, mapOf("Prefer" to "return=representation"))
        } catch (e: IOException) {
            throw AuthException("Can't reach the server. Check your internet connection.")
        }
        if (code == 401) throw AuthException("Your session has expired. Please sign in again.")
        if (code !in 200..299) throw AuthException("Couldn't save your profile ($code). Please try again.")
        // RLS silently filters rows it won't let us change, so an empty result means nothing was saved.
        if (JSONArray(response.ifBlank { "[]" }).length() == 0) throw AuthException("Couldn't save your profile. Please try again.")
    }
}

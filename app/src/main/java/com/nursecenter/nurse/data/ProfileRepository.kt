package com.nursecenter.nurse.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.time.OffsetDateTime

data class NurseProfile(
    val fullName: String,
    /** Public URL of the nurse's profile photo, if they uploaded one. */
    val avatarUrl: String?,
    val email: String,
    val phone: String?,
    /** Qualification label, e.g. "Registered Nurse". */
    val qualification: String?,
    /** The professions the nurse offers, e.g. "Wound Care"; the first is their main one. */
    val professions: List<String>,
    val sancNumber: String?,
    val yearsExperience: Int?,
    val bio: String?,
    /** The home address / service area saved on the website. */
    val serviceArea: String?,
)

/** An address suggestion from Google Places. */
data class PlaceSuggestion(val placeId: String, val description: String)

/** A chosen address with its coordinates. */
data class Place(val name: String, val lat: Double, val lng: Double)

/**
 * Reads and saves the nurse's profile across `profiles`, `profiles_private`, `caregiver_profiles`
 * and the auth user's metadata, matching the web nurse profile page. The mobile number is only
 * saved by the website's SMS verification routes, which also keep it unique across accounts.
 */
object ProfileRepository {
    private const val TAG = "ProfileRepository"

    private val QUALIFICATIONS = mapOf(
        "rn" to "Registered Nurse", "en" to "Enrolled Nurse", "pt" to "Physiotherapist",
        "cg" to "Caregiver Certificate", "na" to "Nursing Assistant", "hbc" to "Home-Based Care", "other" to "Other",
    )

    /** The professions a nurse can choose from, grouped as on the web nurse profile page. */
    val PROFESSIONS: Map<String, List<String>> = linkedMapOf(
        "Registered Nurses" to listOf("Post-Surgery Care", "Post-Surgery", "Injections & IV Therapy", "Wound Care", "ICU / Critical", "Phlebotomist"),
        "Home & Community Nurses" to listOf("Home Nursing Visits", "Chronic Illness Support", "Chronic Illness", "Preventive Health"),
        "Midwives & Maternal Nurses" to listOf("Mother & Baby Care", "Mother & Baby"),
        "Caregivers & Home Care Aides" to listOf("Elderly Care", "Overnight Care", "Home Care"),
        "Physiotherapists" to listOf("Physiotherapy"),
    )

    suspend fun load(): NurseProfile = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val me = session.userId

        coroutineScope {
            val profile = async { HomeRepository.getArray(session, "/rest/v1/profiles?select=full_name,avatar_url&id=eq.$me") }
            val caregiver = async {
                HomeRepository.getArray(
                    session,
                    "/rest/v1/caregiver_profiles?select=*&id=eq.$me",
                )
            }
            // Optional extras: a failure only leaves their field blank.
            val private = async {
                runCatching { HomeRepository.getArray(session, "/rest/v1/profiles_private?select=phone&id=eq.$me").optJSONObject(0) }
                    .onFailure { Log.w(TAG, "profiles_private: ${it.message}") }
                    .getOrNull()
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
            val meta = u?.optJSONObject("user_metadata")
            NurseProfile(
                fullName = p?.optStringOrNull("full_name").orEmpty(),
                avatarUrl = p?.optStringOrNull("avatar_url"),
                email = u?.optStringOrNull("email") ?: session.email,
                // The website mirrors the verified number into the user's metadata too.
                phone = private.await()?.optStringOrNull("phone") ?: meta?.optStringOrNull("phone"),
                qualification = c?.optStringOrNull("qualification_type")?.let { QUALIFICATIONS[it] ?: it },
                professions = professionsOf(c),
                sancNumber = c?.optStringOrNull("sanc_number"),
                yearsExperience = c?.takeUnless { it.isNull("years_experience") }?.optInt("years_experience"),
                bio = c?.optStringOrNull("bio"),
                serviceArea = meta?.optStringOrNull("address_name"),
            )
        }
    }

    /** `specialties`, or the single `specialty` for profiles saved before nurses could choose several. */
    internal fun professionsOf(caregiver: JSONObject?): List<String> {
        val list = caregiver?.optJSONArray("specialties")?.let { array -> (0 until array.length()).map { array.optString(it) } }
            ?.filter { it.isNotBlank() }.orEmpty()
        return list.ifEmpty { listOfNotNull(caregiver?.optStringOrNull("specialty")) }
    }

    /** Saves the fields the app lets the nurse edit; [address] is null when it wasn't changed. */
    suspend fun save(fullName: String, yearsExperience: Int?, bio: String?, professions: List<String>, address: Place?) = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val me = session.userId
        patch(session, "/rest/v1/profiles?id=eq.$me", JSONObject().put("full_name", fullName))
        patch(
            session, "/rest/v1/caregiver_profiles?id=eq.$me",
            JSONObject()
                .put("years_experience", yearsExperience ?: JSONObject.NULL)
                .put("bio", bio ?: JSONObject.NULL)
                .put("specialties", JSONArray(professions))
                // The first profession stays in `specialty` for search and the public profile.
                .put("specialty", professions.firstOrNull() ?: JSONObject.NULL)
                .put("updated_at", OffsetDateTime.now().toString()),
        )
        if (address != null) saveAddress(session, address)
    }

    /** Same as the website: the address lives in the user's metadata, and the map pin in `caregiver_locations`. */
    private fun saveAddress(session: AuthSession, place: Place) {
        val data = JSONObject().put("address_name", place.name).put("address_lat", place.lat).put("address_lng", place.lng)
        val (code, response) = call { SupabaseAuth.request("PUT", "/auth/v1/user", JSONObject().put("data", data).toString(), session.accessToken) }
        if (code == 401) throw AuthException("Your session has expired. Please sign in again.")
        if (code !in 200..299) {
            Log.w(TAG, "address -> $code: $response")
            throw AuthException("Couldn't save your address ($code). Please try again.")
        }

        // The pin only helps nearby search, so a failure here doesn't undo the saved address.
        val pin = JSONObject().put("caregiver_id", session.userId).put("location", "POINT(${place.lng} ${place.lat})")
        runCatching {
            val (pinCode, pinResponse) = SupabaseAuth.request(
                "POST", "/rest/v1/caregiver_locations?on_conflict=caregiver_id", pin.toString(), session.accessToken,
                mapOf("Prefer" to "resolution=merge-duplicates"),
            )
            if (pinCode !in 200..299) Log.w(TAG, "caregiver_locations -> $pinCode: $pinResponse")
        }
    }

    /** Address suggestions in South Africa for what the nurse has typed so far. */
    suspend fun searchPlaces(input: String): List<PlaceSuggestion> = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val (code, body) = call { SupabaseAuth.webRequest("GET", "/api/places?input=${URLEncoder.encode(input, "UTF-8")}", null, session.accessToken) }
        if (code !in 200..299) throw AuthException("Couldn't search addresses ($code).")
        val predictions = JSONObject(body).optJSONArray("predictions") ?: return@withContext emptyList()
        (0 until predictions.length()).map { predictions.getJSONObject(it) }.map {
            PlaceSuggestion(it.getString("place_id"), it.getString("description"))
        }
    }

    /** The coordinates of the suggestion the nurse picked. */
    suspend fun placeDetails(suggestion: PlaceSuggestion): Place = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val (code, body) = call {
            SupabaseAuth.webRequest("GET", "/api/places?place_id=${URLEncoder.encode(suggestion.placeId, "UTF-8")}", null, session.accessToken)
        }
        val location = JSONObject(body.ifBlank { "{}" }).optJSONObject("result")?.optJSONObject("geometry")?.optJSONObject("location")
        if (code !in 200..299 || location == null) throw AuthException("Couldn't look up that address. Please try another.")
        Place(suggestion.description, location.getDouble("lat"), location.getDouble("lng"))
    }

    /** Texts a 6-digit code to [phone]; returns the token to send back with the code. */
    suspend fun sendPhoneCode(phone: String): String = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val json = webPost(session, "/api/auth/send-phone-otp", JSONObject().put("phone", phone))
        json.optStringOrNull("token") ?: throw AuthException("Couldn't send the code. Please try again.")
    }

    /** Checks the code and, if it's right, saves the number; returns the saved number. */
    suspend fun verifyPhoneCode(token: String, code: String): String = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val json = webPost(session, "/api/auth/verify-phone-otp", JSONObject().put("token", token).put("otp", code))
        json.optStringOrNull("phone") ?: throw AuthException("Couldn't verify the code. Please try again.")
    }

    /** POSTs to a website API route; its `error` message is shown to the nurse when it fails. */
    private fun webPost(session: AuthSession, path: String, body: JSONObject): JSONObject {
        val (code, response) = call { SupabaseAuth.webRequest("POST", path, body.toString(), session.accessToken) }
        val json = runCatching { JSONObject(response) }.getOrDefault(JSONObject())
        if (code == 401) throw AuthException("Your session has expired. Please sign in again.")
        if (code !in 200..299) {
            Log.w(TAG, "$path -> $code: $response")
            val message = json.optStringOrNull("error")?.let { if (it.endsWith('.')) it else "$it." }
            throw AuthException(message ?: "Something went wrong ($code). Please try again.")
        }
        return json
    }

    private fun <T> call(block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        throw AuthException("Can't reach the server. Check your internet connection.")
    }

    private fun patch(session: AuthSession, path: String, body: JSONObject) {
        val (code, response) = call {
            SupabaseAuth.request("PATCH", path, body.toString(), session.accessToken, mapOf("Prefer" to "return=representation"))
        }
        if (code == 401) throw AuthException("Your session has expired. Please sign in again.")
        if (code !in 200..299) {
            Log.w(TAG, "$path -> $code: $response")
            throw AuthException("Couldn't save your profile ($code). Please try again.")
        }
        // RLS silently filters rows it won't let us change, so an empty result means nothing was saved.
        if (JSONArray(response.ifBlank { "[]" }).length() == 0) throw AuthException("Couldn't save your profile. Please try again.")
    }
}

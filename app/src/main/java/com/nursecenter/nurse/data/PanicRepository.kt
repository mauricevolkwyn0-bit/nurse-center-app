package com.nursecenter.nurse.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume

/**
 * Raises a panic alert for the signed-in nurse. Inserting into `panic_alerts` triggers the `panic-alert`
 * function, which emails Nurse Center admin the nurse's details and a map link (same as the web panic button).
 */
object PanicRepository {
    private const val LOCATION_TIMEOUT_MS = 8_000L
    private const val FRESH_FIX_MS = 2 * 60_000L

    /** @return true if the alert included the nurse's location. */
    suspend fun send(context: Context): Boolean = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val me = session.userId

        coroutineScope {
            // Details are best effort: the alert must go out even if they can't be read.
            val location = async { currentLocation(context) }
            val name = async {
                runCatching { HomeRepository.getArray(session, "/rest/v1/profiles?select=full_name&id=eq.$me").optJSONObject(0)?.optStringOrNull("full_name") }.getOrNull()
            }
            val phone = async {
                runCatching { HomeRepository.getArray(session, "/rest/v1/profiles_private?select=phone&id=eq.$me").optJSONObject(0)?.optStringOrNull("phone") }.getOrNull()
            }

            val fix = location.await()
            val body = JSONObject()
                .put("nurse_id", me)
                .put("nurse_name", name.await() ?: session.email.substringBefore('@'))
                .put("nurse_email", session.email)
                .put("nurse_phone", phone.await() ?: JSONObject.NULL)
                .put("latitude", fix?.latitude ?: JSONObject.NULL)
                .put("longitude", fix?.longitude ?: JSONObject.NULL)
            insert(session, body)
            fix != null
        }
    }

    private fun insert(session: AuthSession, body: JSONObject) {
        val (code, response) = try {
            // Nurses may insert but not read alerts, so ask for no row back.
            SupabaseAuth.request("POST", "/rest/v1/panic_alerts", body.toString(), session.accessToken, mapOf("Prefer" to "return=minimal"))
        } catch (e: IOException) {
            throw AuthException("Can't reach the server. Check your internet connection.")
        }
        if (code == 401) throw AuthException("Your session has expired. Please sign in again.")
        if (code !in 200..299) {
            android.util.Log.w("PanicRepository", "Panic alert insert failed ($code): $response")
            throw AuthException("Couldn't send your alert ($code).")
        }
    }

    private fun hasLocationPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** A recent fix if one is cached, else a fresh one within [LOCATION_TIMEOUT_MS], else any last known fix, else null. */
    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        val lastKnown = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < FRESH_FIX_MS) return lastKnown
        if (providers.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return lastKnown

        val fresh = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val cancel = CancellationSignal()
                cont.invokeOnCancellation { cancel.cancel() }
                // Network answers fastest indoors; GPS is the fallback when it's off.
                val provider = if (LocationManager.NETWORK_PROVIDER in providers) LocationManager.NETWORK_PROVIDER else providers.first()
                manager.getCurrentLocation(provider, cancel, context.mainExecutor) { location -> if (cont.isActive) cont.resume(location) }
            }
        }
        return fresh ?: lastKnown
    }
}

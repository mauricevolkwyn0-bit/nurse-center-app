package com.nursecenter.nurse.data

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Keeps this phone's Firebase Cloud Messaging token in `profiles_private.push_token`, where the website's
 * `/api/push/notify` looks for it when sending a request to nurses. That push reaches the phone even when
 * the app is closed.
 */
object PushTokens {
    private const val TAG = "PushTokens"

    /** Saves the current token for the signed-in nurse. Best effort: failures are logged, not thrown. */
    suspend fun register() {
        runCatching { save(FirebaseMessaging.getInstance().token.await()) }
            .onFailure { Log.w(TAG, "Couldn't register for push: ${it.message}") }
    }

    /** Called when Firebase issues a new token; only saved while someone is signed in. */
    suspend fun onNewToken(token: String) {
        if (SupabaseAuth.session == null) return
        runCatching { save(token) }.onFailure { Log.w(TAG, "Couldn't save new push token: ${it.message}") }
    }

    /** Stops pushes to this phone before signing out, so the next person on it doesn't get this nurse's requests. */
    suspend fun unregister() {
        runCatching { write(null) }.onFailure { Log.w(TAG, "Couldn't clear push token: ${it.message}") }
        runCatching { FirebaseMessaging.getInstance().deleteToken().await() }
    }

    private suspend fun save(token: String) = write(token)

    private suspend fun write(token: String?) = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val body = JSONObject().put("push_token", token ?: JSONObject.NULL).toString()
        val (code, _) = SupabaseAuth.request(
            "PATCH", "/rest/v1/profiles_private?id=eq.${session.userId}", body, session.accessToken,
            mapOf("Prefer" to "return=minimal"),
        )
        if (code !in 200..299) throw IllegalStateException("profiles_private update failed ($code)")
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            val error = task.exception
            if (error != null) cont.resumeWithException(error) else cont.resume(task.result)
        }
    }
}

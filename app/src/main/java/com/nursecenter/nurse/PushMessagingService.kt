package com.nursecenter.nurse

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nursecenter.nurse.data.PushTokens
import com.nursecenter.nurse.data.RequestRealtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives Firebase pushes, which arrive even when the app is closed. The website sends a data-only
 * `new_booking` message (see nurse-center `app/api/push/notify/route.ts`); it raises the same alert as Realtime.
 */
class PushMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        Log.i(TAG, "Push received: type=${data["type"]}")
        when (data["type"]) {
            "new_booking" -> data["booking_id"]?.takeIf { it.isNotBlank() }?.let { RequestRealtime.onPush(applicationContext, it) }
        }
    }

    override fun onNewToken(token: String) {
        scope.launch { PushTokens.onNewToken(token) }
    }

    private companion object {
        const val TAG = "PushMessagingService"
    }
}

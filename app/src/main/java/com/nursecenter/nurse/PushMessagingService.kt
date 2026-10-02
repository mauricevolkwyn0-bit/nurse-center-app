package com.nursecenter.nurse

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nursecenter.nurse.data.ChatAlert
import com.nursecenter.nurse.data.ChatMessage
import com.nursecenter.nurse.data.ChatUnread
import com.nursecenter.nurse.data.PushTokens
import com.nursecenter.nurse.data.RequestRealtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/**
 * Receives Firebase pushes, which arrive even when the app is closed. The website sends a data-only
 * `new_booking` (see nurse-center `app/api/push/notify/route.ts`), `new_message` (`app/api/push/message/route.ts`)
 * or `booking_cancelled` (`app/api/push/booking-cancelled/route.ts`) message; each raises the same alert as Realtime.
 */
class PushMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        Log.i(TAG, "Push received: type=${data["type"]}")
        when (data["type"]) {
            "new_booking" -> data["booking_id"]?.takeIf { it.isNotBlank() }?.let { RequestRealtime.onPush(applicationContext, it) }
            "new_message" -> onChatMessage(data)
            "booking_cancelled" -> data["booking_id"]?.takeIf { it.isNotBlank() }?.let {
                RequestRealtime.onCancelPush(
                    applicationContext, it,
                    title = data["title"] ?: "Booking cancelled",
                    body = data["body"] ?: "The client cancelled this booking.",
                )
            }
        }
    }

    /** Sent by `app/api/push/message/route.ts` for each new chat message; the body is already a preview. */
    private fun onChatMessage(data: Map<String, String>) {
        val conversationId = data["conversation_id"]?.takeIf { it.isNotBlank() } ?: return
        val message = ChatMessage(
            // Same ID as the Realtime copy, so ChatAlert doesn't list the message twice when both arrive
            id = data["message_id"] ?: "push-${System.currentTimeMillis()}",
            conversationId = conversationId,
            senderId = data["sender_id"].orEmpty(),
            text = data["body"] ?: "Sent you a message",
            createdAt = ZonedDateTime.now(),
            mine = false,
        )
        ChatUnread.onIncoming(message)
        ChatAlert.onIncoming(applicationContext, message, data["title"])
    }

    override fun onNewToken(token: String) {
        scope.launch { PushTokens.onNewToken(token) }
    }

    private companion object {
        const val TAG = "PushMessagingService"
    }
}

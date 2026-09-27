package com.nursecenter.nurse.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.nursecenter.nurse.MainActivity
import com.nursecenter.nurse.R
import kotlinx.coroutines.flow.MutableStateFlow

/** Shows a notification when a client sends the nurse a chat message, and routes taps back to that conversation. */
object ChatAlert {
    private const val CHANNEL_ID = "chat_messages"
    const val EXTRA_CONVERSATION = "com.nursecenter.nurse.CONVERSATION_ID"

    /** True while the app is on screen; set by [MainActivity]. */
    @Volatile var appVisible = false
    /** The conversation the nurse currently has open, so its messages don't also raise a notification. */
    @Volatile var openConversation: String? = null

    /** A conversation the nurse asked to open by tapping a notification; the UI consumes and clears it. */
    val requestedConversation = MutableStateFlow<String?>(null)

    /** Recent unseen messages per conversation, so one notification shows the whole burst. */
    private val pending = mutableMapOf<String, MutableList<ChatMessage>>()

    fun onIncoming(context: Context, message: ChatMessage, senderName: String?) {
        if (appVisible && openConversation == message.conversationId) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "New chat messages from clients"
            }
        )

        val lines = synchronized(pending) {
            pending.getOrPut(message.conversationId) { mutableListOf() }.apply {
                if (none { it.id == message.id }) add(message)
                while (size > 6) removeAt(0)
            }.toList()
        }
        val sender = Person.Builder().setName(senderName ?: "Client").build()
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName("You").build())
        lines.forEach { style.addMessage(it.text, it.createdAt.toInstant().toEpochMilli(), sender) }

        val open = PendingIntent.getActivity(
            context, message.conversationId.hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_CONVERSATION, message.conversationId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(senderName ?: "New message")
            .setContentText(message.text)
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notificationId(message.conversationId), notification) }
    }

    /** Clears the conversation's notification once the nurse has seen it. */
    fun clear(context: Context, conversationId: String) {
        synchronized(pending) { pending.remove(conversationId) }
        NotificationManagerCompat.from(context).cancel(notificationId(conversationId))
    }

    fun reset() {
        synchronized(pending) { pending.clear() }
        openConversation = null
        requestedConversation.value = null
    }

    fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_CONVERSATION)?.let { requestedConversation.value = it }
    }

    // Offset so chat IDs can't collide with request notifications, which use the booking ID's hash.
    private fun notificationId(conversationId: String) = conversationId.hashCode() xor 0x43484154
}

package com.nursecenter.nurse.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.nursecenter.nurse.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Listens to Supabase Realtime for new client requests addressed to the signed-in nurse
 * (pending `bookings` assigned to them and `job_alerts` rows sent to them) and for chat `messages`.
 */
object RequestRealtime {
    private const val TAG = "RequestRealtime"
    private const val BOOKINGS_TOPIC = "realtime:nurse-bookings"
    private const val ALERTS_TOPIC = "realtime:nurse-job-alerts"
    private const val MESSAGES_TOPIC = "realtime:nurse-messages"
    private const val STATUS_TOPIC = "realtime:nurse-status"
    private const val SUPPORT_TOPIC = "realtime:nurse-support"
    private val TOPICS = listOf(BOOKINGS_TOPIC, ALERTS_TOPIC, MESSAGES_TOPIC, STATUS_TOPIC, SUPPORT_TOPIC)

    private val _changes = MutableStateFlow(0)
    /** Increments whenever a request arrives or is closed (cancelled, declined, taken); screens observe it to refresh. */
    val changes: StateFlow<Int> = _changes

    private val _messages = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 64)
    /** Chat messages as they arrive in any of the nurse's conversations, including their own. */
    val messages: SharedFlow<ChatMessage> = _messages

    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private val ref = AtomicInteger(0)
    private val alerted = mutableSetOf<String>()
    /** Bookings already reported as closed, so the bookings and job_alerts events for one change only act once. */
    private val closed = mutableSetOf<String>()
    private var scope: CoroutineScope? = null

    fun start(context: Context) {
        if (scope != null) return
        val app = context.applicationContext
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { it.launch { connectLoop(app) } }
    }

    fun stop() {
        scope?.cancel()
        scope = null
        synchronized(alerted) { alerted.clear() }
        synchronized(closed) { closed.clear() }
        ChatAlert.reset()
        ChatUnread.clear()
        NurseStatus.clear()
        IncomingRequestAlert.cancelReminders()
        SupportRepository.reset()
    }

    private suspend fun CoroutineScope.connectLoop(context: Context) {
        var backoff = 1_000L
        while (isActive) {
            val session = runCatching { SupabaseAuth.validSession() }.getOrNull()
            if (session != null) {
                val closed = CompletableDeferred<Unit>()
                val listener = Listener(context, this, session.userId, closed) { backoff = 1_000L }
                val url = BuildConfig.SUPABASE_URL.trimEnd('/').replaceFirst("http", "ws") +
                    "/realtime/v1/websocket?apikey=${BuildConfig.SUPABASE_ANON_KEY}&vsn=1.0.0"
                val socket = client.newWebSocket(Request.Builder().url(url).build(), listener)
                val heartbeat = keepAlive(socket, session.accessToken)
                try {
                    closed.await()
                } finally {
                    heartbeat.cancel()
                    socket.cancel()
                }
            }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(30_000L)
        }
    }

    /** Sends Phoenix heartbeats and pushes refreshed access tokens so the channels stay authorised. */
    private fun CoroutineScope.keepAlive(socket: WebSocket, initialToken: String): Job = launch {
        var token = initialToken
        while (isActive) {
            delay(25_000)
            socket.send(message("phoenix", "heartbeat", JSONObject()))
            val fresh = runCatching { SupabaseAuth.validSession().accessToken }.getOrNull() ?: continue
            if (fresh != token) {
                token = fresh
                for (topic in TOPICS) {
                    socket.send(message(topic, "access_token", JSONObject().put("access_token", fresh)))
                }
            }
        }
    }

    private fun message(topic: String, event: String, payload: JSONObject): String {
        val r = ref.incrementAndGet().toString()
        return JSONObject().put("topic", topic).put("event", event).put("payload", payload)
            .put("ref", r).put("join_ref", r).toString()
    }

    /** @param filter a Realtime row filter, or null to rely on the table's RLS policy alone. */
    private fun joinPayload(table: String, events: List<String>, filter: String?, token: String) = JSONObject()
        .put(
            "config", JSONObject()
                .put("broadcast", JSONObject().put("self", false))
                .put("presence", JSONObject().put("key", ""))
                .put(
                    "postgres_changes", JSONArray().apply {
                        events.forEach { event ->
                            put(
                                JSONObject().put("event", event).put("schema", "public").put("table", table)
                                    .apply { if (filter != null) put("filter", filter) }
                            )
                        }
                    }
                )
        )
        .put("access_token", token)

    /**
     * A new request delivered by push (works while the app is closed). Shares [raise] with Realtime, so a
     * request that arrives both ways only alerts once. The push is sent as the request is created, so the
     * response window starts now.
     */
    fun onPush(context: Context, bookingId: String) = raise(context.applicationContext, bookingId, sentAt = null)

    /** Asks screens to reload, e.g. when the app comes back to the foreground and live updates may have been missed. */
    fun refresh() {
        _changes.value++
    }

    /** @param sentAt the record's `created_at`, which starts the nurse's response window. */
    private fun raise(context: Context, bookingId: String, sentAt: String?) {
        val start = sentAt?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
            ?: System.currentTimeMillis()
        val expiresAt = start + RESPONSE_WINDOW.toMillis()
        if (expiresAt <= System.currentTimeMillis()) return
        // Offline nurses aren't alerted, matching the website. Unknown status fails open so requests aren't missed.
        if (NurseStatus.online.value == false) {
            Log.i(TAG, "Nurse is offline; not alerting for booking $bookingId")
            _changes.value++
            return
        }
        val isNew = synchronized(alerted) { alerted.add(bookingId) }
        if (!isNew) return
        Log.i(TAG, "New request for booking $bookingId")
        IncomingRequestAlert.fire(context, bookingId, expiresAt)
        _changes.value++
    }

    /**
     * A request ended on the other side (client cancelled, another nurse took it, it was withdrawn):
     * silence its alert, clear its notification, refresh the screens and tell the nurse why.
     * @param message shown to the nurse, or null when the change needs no explanation (e.g. they accepted it).
     */
    private fun close(context: Context, bookingId: String, message: String?) {
        val isNew = synchronized(closed) { closed.add(bookingId) }
        if (!isNew) return
        Log.i(TAG, "Request closed for booking $bookingId")
        IncomingRequestAlert.dismiss(context, bookingId)
        _changes.value++
        if (message != null) {
            Handler(Looper.getMainLooper()).post { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
        }
    }

    private class Listener(
        private val context: Context,
        private val scope: CoroutineScope,
        private val userId: String,
        private val closed: CompletableDeferred<Unit>,
        private val onConnected: () -> Unit,
    ) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val token = SupabaseAuth.session?.accessToken ?: return run { webSocket.close(1000, null) }
            // Separate channels so one table missing from the realtime publication can't break the other.
            val mine = "caregiver_id=eq.$userId"
            webSocket.send(message(BOOKINGS_TOPIC, "phx_join", joinPayload("bookings", listOf("*"), mine, token)))
            // UPDATEs arrive when the booking behind an alert is cancelled or taken (see migration 022).
            webSocket.send(message(ALERTS_TOPIC, "phx_join", joinPayload("job_alerts", listOf("INSERT", "UPDATE"), mine, token)))
            // messages has no caregiver column; its RLS policy limits delivery to the nurse's own conversations.
            webSocket.send(message(MESSAGES_TOPIC, "phx_join", joinPayload("messages", listOf("INSERT"), null, token)))
            // Online/Offline changes made on the website.
            webSocket.send(message(STATUS_TOPIC, "phx_join", joinPayload("caregiver_profiles", listOf("UPDATE"), "id=eq.$userId", token)))
            // Support replies; RLS limits delivery to the nurse's own support thread.
            webSocket.send(message(SUPPORT_TOPIC, "phx_join", joinPayload("support_messages", listOf("INSERT"), null, token)))
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
            val payload = msg.optJSONObject("payload") ?: return
            when (msg.optString("event")) {
                "phx_reply" -> if (payload.optString("status") == "ok") {
                    val topic = msg.optString("topic")
                    // Heartbeats reply on "phoenix"; a reply with a response is a channel join.
                    if (topic != "phoenix" && payload.optJSONObject("response")?.has("postgres_changes") == true) {
                        Log.i(TAG, "Joined $topic")
                    }
                    if (topic != "phoenix") onConnected()
                } else {
                    Log.w(TAG, "Join failed on ${msg.optString("topic")}: $payload")
                }
                "system" -> if (payload.optString("status") == "error") Log.w(TAG, "Realtime error: $payload")
                "postgres_changes" -> handleChange(payload.optJSONObject("data") ?: return)
            }
        }

        private fun handleChange(data: JSONObject) {
            val record = data.optJSONObject("record") ?: return
            val type = data.optString("type")
            when (data.optString("table")) {
                "bookings" -> {
                    val id = record.optString("id").takeIf { it.isNotBlank() } ?: return
                    val status = record.optString("status")
                    when {
                        type != "INSERT" && type != "UPDATE" -> Unit
                        status == "pending" && record.isNull("deleted_at") ->
                            raise(context, id, record.optStringOrNull("created_at"))
                        // The nurse's own decline also arrives here; only explain changes made by someone else.
                        status == "cancelled" && record.optStringOrNull("cancelled_by") != userId ->
                            close(context, id, "The client cancelled this booking.")
                        status == "cancelled" || !record.isNull("deleted_at") -> close(context, id, null)
                    }
                }
                "job_alerts" -> {
                    val bookingId = record.optString("booking_id").takeIf { it.isNotBlank() } ?: return
                    when {
                        type == "INSERT" && record.isNull("closed_at") ->
                            raise(context, bookingId, record.optStringOrNull("created_at"))
                        type == "UPDATE" && !record.isNull("closed_at") -> close(
                            context, bookingId,
                            when (record.optString("closed_reason")) {
                                "cancelled", "deleted" -> "The client cancelled this request."
                                "taken" -> "Another nurse accepted this request."
                                else -> null
                            },
                        )
                    }
                }
                "support_messages" -> if (type == "INSERT") {
                    val message = runCatching { SupportRepository.toMessage(record) }.getOrNull() ?: return
                    _messages.tryEmit(message)
                    if (!message.mine) {
                        ChatUnread.onIncoming(message)
                        ChatAlert.onIncoming(context, message, "Nurse Center Support")
                    }
                }
                "caregiver_profiles" -> if (record.has("is_available") && !record.isNull("is_available")) {
                    NurseStatus.onRemoteChange(record.getBoolean("is_available"))
                }
                "messages" -> if (type == "INSERT" && record.isNull("deleted_at")) {
                    val message = runCatching { ChatRepository.toMessage(record, userId) }.getOrNull() ?: return
                    _messages.tryEmit(message)
                    if (!message.mine) {
                        ChatUnread.onIncoming(message)
                        scope.launch { ChatAlert.onIncoming(context, message, ChatRepository.senderName(message.senderId)) }
                    }
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            closed.complete(Unit)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "Realtime connection lost: ${t.message}")
            closed.complete(Unit)
        }
    }
}

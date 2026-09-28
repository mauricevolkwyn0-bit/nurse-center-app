package com.nursecenter.nurse.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap

data class ChatThread(
    val id: String,
    val clientName: String,
    val lastMessage: String?,
    val lastAt: ZonedDateTime?,
    val unread: Int,
)

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val text: String,
    val createdAt: ZonedDateTime,
    val mine: Boolean,
)

/** Reads and sends the nurse's client chats in the Supabase `conversations` and `messages` tables. */
object ChatRepository {
    private const val TAG = "ChatRepository"
    private const val MESSAGE_FIELDS = "id,conversation_id,sender_id,content,message_type,created_at"
    private val names = ConcurrentHashMap<String, String>()

    /** The nurse's conversations, most recently active first. */
    suspend fun loadThreads(): List<ChatThread> = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val me = session.userId
        val convos = objects(
            HomeRepository.getArray(
                session,
                "/rest/v1/conversations?select=id,patient_id,last_message_at,created_at" +
                    "&caregiver_id=eq.$me&order=last_message_at.desc.nullslast",
            )
        )
        if (convos.isEmpty()) return@withContext emptyList()
        val ids = convos.joinToString(",") { it.getString("id") }
        val patientIds = convos.mapNotNull { it.optStringOrNull("patient_id") }.distinct().joinToString(",")

        coroutineScope {
            val profiles = async { HomeRepository.getArray(session, "/rest/v1/profiles?select=id,full_name&id=in.($patientIds)") }
            val recent = async {
                HomeRepository.getArray(
                    session,
                    "/rest/v1/messages?select=conversation_id,content,message_type,created_at&conversation_id=in.($ids)" +
                        "&deleted_at=is.null&order=created_at.desc&limit=${maxOf(convos.size * 5, 50)}",
                )
            }
            val unread = async {
                HomeRepository.getArray(
                    session,
                    "/rest/v1/messages?select=conversation_id&conversation_id=in.($ids)" +
                        "&sender_id=neq.$me&read_at=is.null&deleted_at=is.null",
                )
            }

            objects(profiles.await()).forEach { p -> p.optStringOrNull("full_name")?.let { names[p.getString("id")] = it } }
            val latest = objects(recent.await()).groupBy { it.getString("conversation_id") }.mapValues { it.value.first() }
            val unreadCounts = objects(unread.await()).groupingBy { it.getString("conversation_id") }.eachCount()
            val zone = ZoneId.systemDefault()

            convos.map { c ->
                val id = c.getString("id")
                val last = latest[id]
                ChatThread(
                    id = id,
                    clientName = c.optStringOrNull("patient_id")?.let { names[it] } ?: "Client",
                    lastMessage = last?.let { displayText(it) },
                    lastAt = (last?.optStringOrNull("created_at") ?: c.optStringOrNull("last_message_at"))?.let { parseTime(it, zone) },
                    unread = unreadCounts[id] ?: 0,
                )
            }.sortedByDescending { it.lastAt }
        }
    }

    /** Every message in the conversation, oldest first. */
    suspend fun loadMessages(conversationId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val rows = HomeRepository.getArray(
            session,
            "/rest/v1/messages?select=$MESSAGE_FIELDS&conversation_id=eq.$conversationId&deleted_at=is.null&order=created_at.asc",
        )
        objects(rows).map { toMessage(it, session.userId) }
    }

    suspend fun send(conversationId: String, text: String): ChatMessage = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val body = JSONObject()
            .put("conversation_id", conversationId)
            .put("sender_id", session.userId)
            .put("content", text)
            .put("message_type", "text")
        val (code, response) = try {
            SupabaseAuth.request(
                "POST", "/rest/v1/messages?select=$MESSAGE_FIELDS", body.toString(), session.accessToken,
                mapOf("Prefer" to "return=representation"),
            )
        } catch (e: IOException) {
            throw AuthException("Can't reach the server. Check your internet connection.")
        }
        if (code == 401) throw AuthException("Your session has expired. Please sign in again.")
        if (code !in 200..299) throw AuthException("Couldn't send your message ($code). Please try again.")
        toMessage(JSONArray(response).getJSONObject(0), session.userId)
    }

    /** True when a client has sent the nurse a message they haven't read yet, in any conversation. */
    suspend fun hasUnread(): Boolean = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val me = session.userId
        val convos = HomeRepository.getArray(session, "/rest/v1/conversations?select=id&caregiver_id=eq.$me")
        if (convos.length() == 0) return@withContext false
        val ids = objects(convos).joinToString(",") { it.getString("id") }
        HomeRepository.getArray(
            session,
            "/rest/v1/messages?select=id&conversation_id=in.($ids)&sender_id=neq.$me&read_at=is.null&deleted_at=is.null&limit=1",
        ).length() > 0
    }

    /**
     * Marks the client's messages in the conversation as read. Goes through the `mark_conversation_read`
     * function (migration 033) because RLS only lets a message's sender update it. Best effort: failures are ignored.
     */
    suspend fun markRead(conversationId: String) = withContext(Dispatchers.IO) {
        runCatching {
            val session = SupabaseAuth.validSession()
            val body = JSONObject().put("p_conversation_id", conversationId).toString()
            val (code, _) = SupabaseAuth.request("POST", "/rest/v1/rpc/mark_conversation_read", body, session.accessToken)
            if (code !in 200..299) Log.w(TAG, "mark_conversation_read failed ($code)")
        }
        Unit
    }

    /** The display name of a message sender, cached after the first lookup. */
    suspend fun senderName(userId: String): String? = names[userId] ?: withContext(Dispatchers.IO) {
        runCatching {
            val session = SupabaseAuth.validSession()
            HomeRepository.getArray(session, "/rest/v1/profiles?select=full_name&id=eq.$userId")
                .optJSONObject(0)?.optStringOrNull("full_name")
        }.getOrNull()?.also { names[userId] = it }
    }

    internal fun toMessage(json: JSONObject, me: String): ChatMessage {
        val sender = json.optString("sender_id")
        return ChatMessage(
            id = json.getString("id"),
            conversationId = json.getString("conversation_id"),
            senderId = sender,
            text = displayText(json),
            createdAt = parseTime(json.getString("created_at"), ZoneId.systemDefault()),
            mine = sender == me,
        )
    }

    /** The message's text, or a short description for photos and shared locations. */
    private fun displayText(json: JSONObject): String = json.optStringOrNull("content") ?: when (json.optString("message_type")) {
        "image" -> "Sent a photo"
        "location" -> "Shared a location"
        else -> "Message"
    }

    private fun parseTime(value: String, zone: ZoneId): ZonedDateTime = OffsetDateTime.parse(value).atZoneSameInstant(zone)

    private fun objects(rows: JSONArray): List<JSONObject> = (0 until rows.length()).map { rows.getJSONObject(it) }
}

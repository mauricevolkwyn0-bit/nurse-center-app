package com.nursecenter.nurse.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** The ID the app uses for the Nurse Center Support chat in place of a `conversations` ID. */
const val SUPPORT_CHAT_ID = "support"
const val SUPPORT_PHONE = "+27704256338"

data class SupportSummary(val lastMessage: String?, val lastAt: ZonedDateTime?, val unread: Int)

/**
 * The nurse's chat with Nurse Center support, in `support_threads` / `support_messages` (migration 023).
 * Staff read and reply from the admin panel's Support Chats page.
 */
object SupportRepository {
    private const val FIELDS = "id,sender_id,from_staff,content,created_at"
    @Volatile private var threadId: String? = null

    /** Preview for the chat list; null fields when the nurse hasn't messaged support yet. */
    suspend fun summary(): SupportSummary = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val thread = existingThread(session) ?: return@withContext SupportSummary(null, null, 0)
        val last = get(
            session, "/rest/v1/support_messages?select=content,created_at&thread_id=eq.$thread&order=created_at.desc&limit=1",
        ).optJSONObject(0)
        val unread = get(
            session, "/rest/v1/support_messages?select=id&thread_id=eq.$thread&from_staff=is.true&read_at=is.null",
        ).length()
        SupportSummary(
            lastMessage = last?.optStringOrNull("content"),
            lastAt = last?.optStringOrNull("created_at")?.let { OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault()) },
            unread = unread,
        )
    }

    suspend fun loadMessages(): List<ChatMessage> = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val thread = existingThread(session) ?: return@withContext emptyList()
        val rows = get(session, "/rest/v1/support_messages?select=$FIELDS&thread_id=eq.$thread&order=created_at.asc")
        (0 until rows.length()).map { toMessage(rows.getJSONObject(it)) }
    }

    /** Sends a message, starting the support thread on the nurse's first message. */
    suspend fun send(text: String): ChatMessage = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val thread = existingThread(session) ?: createThread(session)
        val body = JSONObject().put("thread_id", thread).put("sender_id", session.userId).put("content", text)
        val (code, response) = request(session, "POST", "/rest/v1/support_messages?select=$FIELDS", body.toString())
        if (code == 404) throw AuthException(UNAVAILABLE)
        if (code !in 200..299) throw AuthException("Couldn't send your message ($code). Please try again.")
        toMessage(JSONArray(response).getJSONObject(0))
    }

    /** True when support has replied and the nurse hasn't read it yet. */
    suspend fun hasUnread(): Boolean = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val thread = existingThread(session) ?: return@withContext false
        get(session, "/rest/v1/support_messages?select=id&thread_id=eq.$thread&from_staff=is.true&read_at=is.null&limit=1").length() > 0
    }

    /** Marks support's replies as read. Best effort. */
    suspend fun markRead() = withContext(Dispatchers.IO) {
        runCatching {
            val session = SupabaseAuth.validSession()
            val thread = existingThread(session) ?: return@runCatching
            val body = JSONObject().put("read_at", OffsetDateTime.now().toString()).toString()
            request(session, "PATCH", "/rest/v1/support_messages?thread_id=eq.$thread&from_staff=is.true&read_at=is.null", body)
        }
        Unit
    }

    fun reset() {
        threadId = null
    }

    /** A `support_messages` row as a chat message; the nurse's own messages are the ones not from staff. */
    internal fun toMessage(json: JSONObject) = ChatMessage(
        id = json.getString("id"),
        conversationId = SUPPORT_CHAT_ID,
        senderId = json.optStringOrNull("sender_id") ?: "support",
        text = json.optString("content"),
        createdAt = OffsetDateTime.parse(json.getString("created_at")).atZoneSameInstant(ZoneId.systemDefault()),
        mine = !json.optBoolean("from_staff"),
    )

    private fun existingThread(session: AuthSession): String? = threadId ?: get(
        session, "/rest/v1/support_threads?select=id&user_id=eq.${session.userId}",
    ).optJSONObject(0)?.optStringOrNull("id")?.also { threadId = it }

    /** GET that explains a missing support table (migration 023 not applied) instead of a bare 404. */
    private fun get(session: AuthSession, path: String): JSONArray {
        val (code, body) = try {
            SupabaseAuth.request("GET", path, null, session.accessToken)
        } catch (e: IOException) {
            throw AuthException("Can't reach the server. Check your internet connection.")
        }
        if (code == 401) throw AuthException("Your session has expired. Please sign in again.")
        if (code == 404) throw AuthException(UNAVAILABLE)
        if (code !in 200..299) throw AuthException("Couldn't load your support chat ($code). Please try again.")
        return JSONArray(body)
    }

    private const val UNAVAILABLE =
        "Support chat isn't available yet. Please call support or email info@nursecenter.co.za in the meantime."

    private fun createThread(session: AuthSession): String {
        val (code, response) = request(
            session, "POST", "/rest/v1/support_threads?select=id", JSONObject().put("user_id", session.userId).toString(),
        )
        return when {
            code in 200..299 -> JSONArray(response).getJSONObject(0).getString("id").also { threadId = it }
            // Created meanwhile (e.g. from another device): use that one.
            code == 409 -> existingThread(session) ?: throw AuthException("Couldn't start a support chat. Please try again.")
            code == 404 -> throw AuthException(UNAVAILABLE)
            else -> throw AuthException("Couldn't start a support chat ($code). Please try again.")
        }
    }

    private fun request(session: AuthSession, method: String, path: String, body: String): Pair<Int, String> {
        val result = try {
            SupabaseAuth.request(method, path, body, session.accessToken, mapOf("Prefer" to "return=representation"))
        } catch (e: IOException) {
            throw AuthException("Can't reach the server. Check your internet connection.")
        }
        if (result.first == 401) throw AuthException("Your session has expired. Please sign in again.")
        return result
    }
}

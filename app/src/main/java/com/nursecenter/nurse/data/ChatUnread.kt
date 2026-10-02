package com.nursecenter.nurse.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/** Whether the nurse has unread client or support messages; drives the dot on the Chat tab. */
object ChatUnread {
    private const val TAG = "ChatUnread"
    private val _any = MutableStateFlow(false)
    val any: StateFlow<Boolean> = _any

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Job? = null
    /** Bumped by every check and every incoming message, so a slow, older check can't overwrite newer state. */
    private val generation = AtomicInteger()

    /** Re-reads the unread state from Supabase. Best effort: keeps the last value if either check fails. */
    suspend fun refresh() {
        val gen = generation.incrementAndGet()
        runCatching {
            coroutineScope {
                val clients = async { ChatRepository.hasUnread() }
                val support = async { SupportRepository.hasUnread() }
                clients.await() || support.await()
            }
        }.onSuccess { unread ->
            if (generation.get() == gen) _any.value = unread
        }.onFailure { Log.w(TAG, "Couldn't check unread messages: ${it.message}") }
    }

    /** Refreshes shortly, collapsing bursts (marking a chat read sends one live update per message) into one check. */
    fun requestRefresh() {
        synchronized(this) {
            pending?.cancel()
            pending = scope.launch {
                delay(400)
                refresh()
            }
        }
    }

    /** A message from a client or support arrived; it's unread unless that conversation is on screen. */
    fun onIncoming(message: ChatMessage) {
        generation.incrementAndGet()
        if (!(ChatAlert.appVisible && ChatAlert.openConversation == message.conversationId)) _any.value = true
    }

    fun clear() {
        synchronized(this) { pending?.cancel() }
        generation.incrementAndGet()
        _any.value = false
    }
}

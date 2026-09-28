package com.nursecenter.nurse.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Whether the nurse has unread client or support messages; drives the dot on the Chat tab. */
object ChatUnread {
    private val _any = MutableStateFlow(false)
    val any: StateFlow<Boolean> = _any

    /** Re-reads the unread state from Supabase. Best effort: keeps the last value if either check fails. */
    suspend fun refresh() {
        runCatching {
            coroutineScope {
                val clients = async { ChatRepository.hasUnread() }
                val support = async { SupportRepository.hasUnread() }
                _any.value = clients.await() || support.await()
            }
        }
    }

    /** A message from a client or support arrived; it's unread unless that conversation is on screen. */
    fun onIncoming(message: ChatMessage) {
        if (!(ChatAlert.appVisible && ChatAlert.openConversation == message.conversationId)) _any.value = true
    }

    fun clear() {
        _any.value = false
    }
}

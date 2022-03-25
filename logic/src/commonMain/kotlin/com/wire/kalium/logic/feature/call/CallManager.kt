package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

expect class CallManager {

    suspend fun onCallingMessageReceived(message: Message, content: MessageContent.Calling)
    val allCalls: StateFlow<List<Call>>
}

val CallManager.ongoingCalls get() = allCalls.map {
    it.filter { call ->
        call.status in listOf("incoming", "answered", "established")
    }
}

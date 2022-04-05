package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

interface CallManager {

    suspend fun onCallingMessageReceived(message: Message, content: MessageContent.Calling)
    suspend fun startCall(conversationId: ConversationId, callType: Int, conversationType: Int, isAudioCbr: Boolean = false)
    val allCalls: StateFlow<List<Call>>
}

expect class CallManagerImpl: CallManager
val CallManager.ongoingCalls get() = allCalls.map {
    it.filter { call ->
        call.status in listOf(
            CallStatus.INCOMING,
            CallStatus.ANSWERED,
            CallStatus.ESTABLISHED
        )
    }
}

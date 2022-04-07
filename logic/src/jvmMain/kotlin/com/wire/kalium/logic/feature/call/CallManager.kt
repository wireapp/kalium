package com.wire.kalium.logic.feature.call

import com.wire.kalium.calling.CallType
import com.wire.kalium.calling.CallingConversationType
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class CallManagerImpl : CallManager {

    private val _calls = MutableStateFlow(listOf<Call>())
    override val allCalls = _calls.asStateFlow()

    init {
        kaliumLogger.w("CallManager initialized for JVM but no supported yet.")
    }

    override suspend fun onCallingMessageReceived(message: Message, content: MessageContent.Calling) {
        kaliumLogger.w("onCallingMessageReceived for JVM but no supported yet.")
    }

    override suspend fun startCall(conversationId: ConversationId, callType: CallType, conversationType: CallingConversationType, isAudioCbr: Boolean) {
        kaliumLogger.w("startCall for JVM but no supported yet.")
    }
}

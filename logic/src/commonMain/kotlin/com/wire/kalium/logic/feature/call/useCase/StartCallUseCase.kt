package com.wire.kalium.logic.feature.call.useCase

import com.wire.kalium.calling.CallType
import com.wire.kalium.calling.CallingConversationType
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager

class StartCallUseCase(private val callManager: CallManager) {

    suspend operator fun invoke(conversationId: ConversationId, callType: CallType, callingConversationType: CallingConversationType) {
        callManager.startCall(conversationId, callType, callingConversationType)
    }
}

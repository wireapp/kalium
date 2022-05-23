package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager

class EndCallUseCase(private val callManager: Lazy<CallManager>) {

    suspend operator fun invoke(conversationId: ConversationId) {
        callManager.value.endCall(conversationId)
    }
}

package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager

/**
 * This use case will reject a call for the given conversation.
 */
class RejectCallUseCase(private val callManager: Lazy<CallManager>) {

    suspend operator fun invoke(conversationId: ConversationId) {
        callManager.value.rejectCall(conversationId)
    }
}

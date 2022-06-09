package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager

class MuteCallUseCase(
    private val callManager: Lazy<CallManager>,
    private val callRepository: CallRepository
) {
    suspend operator fun invoke(conversationId: ConversationId) {
        callManager.value.muteCall(true)
        callRepository.updateIsMutedById(
            conversationId = conversationId.toString(),
            isMuted = true
        )
    }
}

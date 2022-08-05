package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager

class MuteCallUseCase(
    private val callManager: Lazy<CallManager>,
    private val callRepository: CallRepository
) {
    suspend operator fun invoke(conversationId: ConversationId) {
        callRepository.updateIsMutedById(
            conversationId = conversationId.toString(),
            isMuted = true
        )
        // We should call AVS muting method only for established call, otherwise incoming call could mute/un-mute the current call
        callRepository.getCallMetadataProfile()[conversationId.toString()]?.establishedTime?.let {
            callManager.value.muteCall(true)
        }
    }
}

package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.VideoState
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager
import kotlinx.coroutines.flow.first

class UpdateVideoStateUseCase(
    private val callManager: Lazy<CallManager>,
    private val callRepository: CallRepository
) {

    suspend operator fun invoke(
        conversationId: ConversationId,
        videoState: VideoState
    ) {
        if (videoState != VideoState.PAUSED)
            callRepository.updateIsCameraOnById(conversationId.toString(), videoState == VideoState.STARTED)

        // updateVideoState should be called only when the call is established
        callRepository.establishedCallsFlow().first().find { call ->
            call.conversationId == conversationId
        }?.let {
            callManager.value.updateVideoState(conversationId, videoState)
        }
    }
}

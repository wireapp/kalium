package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.VideoState
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager

class UpdateVideoStateUseCase(
    private val callManager: Lazy<CallManager>,
    private val callRepository: CallRepository
) {

    suspend operator fun invoke(conversationId: ConversationId, videoState: VideoState) {
        callManager.value.updateVideoState(conversationId, videoState)
        callRepository.updateIsCameraOnById(conversationId.toString(), videoState == VideoState.STARTED)
    }
}

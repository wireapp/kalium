package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.VideoState
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.call.CallStatus
import kotlinx.coroutines.flow.first

class UpdateVideoStateUseCase(
    private val callManager: Lazy<CallManager>,
    private val callRepository: CallRepository
) {

    suspend operator fun invoke(
        conversationId: ConversationId,
        videoState: VideoState
    ) {
        callRepository.updateIsCameraOnById(conversationId.toString(), videoState == VideoState.STARTED)

        //updateVideoState should be called only when the call is answered/established
        callRepository.callsFlow().first().find { call ->
            call.conversationId == conversationId
        }?.status?.also {
            if (it == CallStatus.ESTABLISHED || it == CallStatus.ANSWERED)
                callManager.value.updateVideoState(conversationId, videoState)
        }
    }
}

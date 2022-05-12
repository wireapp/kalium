package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.VideoState
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager

class UpdateVideoStateUseCase(private val callManager: CallManager) {

    suspend operator fun invoke(conversationId: ConversationId, videoState: VideoState) {
        callManager.updateVideoState(conversationId, videoState)
    }
}

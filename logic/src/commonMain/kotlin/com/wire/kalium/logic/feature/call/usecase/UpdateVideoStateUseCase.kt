/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.VideoState
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager
import kotlinx.coroutines.flow.first

/**
 * This use case is responsible for updating the video state of a call.
 * @see [VideoState]
 */
class UpdateVideoStateUseCase(
    private val callManager: Lazy<CallManager>,
    private val callRepository: CallRepository
) {
    /**
     * @param conversationId the id of the conversation.
     * @param videoState the new video state of the call.
     */
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

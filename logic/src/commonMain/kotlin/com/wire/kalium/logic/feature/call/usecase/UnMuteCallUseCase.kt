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
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager
import kotlinx.coroutines.flow.first

/**
 * This use case is responsible for un-mute a call.
 */
class UnMuteCallUseCase(
    private val callManager: Lazy<CallManager>,
    private val callRepository: CallRepository
) {
    /**
     * We should call AVS muting method only for established call, otherwise incoming call could mute/un-mute the current call
     * @param conversationId the id of the conversation.
     */
    suspend operator fun invoke(conversationId: ConversationId) {
        callRepository.updateIsMutedById(
            conversationId = conversationId.toString(),
            isMuted = false
        )
        val activeCall = callRepository.establishedCallsFlow().first().find {
            it.conversationId == conversationId
        }

        // We should call AVS muting method only for established call, otherwise incoming call could mute the current call
        activeCall?.let { callManager.value.muteCall(false) }
    }
}

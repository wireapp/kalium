/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
import io.mockative.Mockable

/**
 * This use case is responsible for un-mute a call.
 */
@Mockable
interface UnMuteCallUseCase {
    suspend operator fun invoke(
        conversationId: ConversationId,
        shouldApplyOnDeviceMicrophone: Boolean = true
    )
}

internal class UnMuteCallUseCaseImpl(
    private val callManager: Lazy<CallManager>,
    private val callRepository: CallRepository
) : UnMuteCallUseCase {

    /**
     * @param conversationId the id of the conversation.
     * @param shouldApplyOnDeviceMicrophone to be used mainly in preview calling screen to not allow muting device microphone
     */
    override suspend operator fun invoke(
        conversationId: ConversationId,
        shouldApplyOnDeviceMicrophone: Boolean
    ) {
        callRepository.updateIsMutedById(
            conversationId = conversationId,
            isMuted = false
        )

        if (shouldApplyOnDeviceMicrophone) {
            callManager.value.muteCall(false)
        }
    }
}

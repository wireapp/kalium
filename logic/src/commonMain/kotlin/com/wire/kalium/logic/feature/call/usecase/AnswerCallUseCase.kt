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

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * This use case is responsible for answering a call.
 */
interface AnswerCallUseCase {
    suspend operator fun invoke(
        conversationId: ConversationId
    )
}

internal class AnswerCallUseCaseImpl(
    private val observeOngoingAndIncomingCalls: ObserveOngoingAndIncomingCallsUseCase,
    private val callManager: Lazy<CallManager>,
    private val muteCall: MuteCallUseCase,
    private val kaliumConfigs: KaliumConfigs,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : AnswerCallUseCase {

    /**
     * @param conversationId the id of the conversation.
     */
    override suspend fun invoke(
        conversationId: ConversationId
    ) {
        val call = observeOngoingAndIncomingCalls().first().find { it.conversationId == conversationId }
        val isVideoCall = call?.isCameraOn ?: false
        withContext(dispatchers.default) {
            callManager.value.answerCall(
                conversationId = conversationId,
                isAudioCbr = kaliumConfigs.forceConstantBitrateCalls,
                isVideoCall = isVideoCall
            ).also {
                if (call?.isMuted == true) {
                    muteCall(conversationId)
                }
            }
        }
    }
}

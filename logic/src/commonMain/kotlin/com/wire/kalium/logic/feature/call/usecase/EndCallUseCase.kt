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

import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.feature.user.ShouldAskCallFeedbackUseCase
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * This use case is responsible for ending a call.
 */
interface EndCallUseCase {

    /**
     * @param conversationId the id of the conversation for the call should be ended.
     */
    suspend operator fun invoke(conversationId: ConversationId)
}

/**
 * This use case is responsible for ending a call.
 */
internal class EndCallUseCaseImpl(
    private val callManager: Lazy<CallManager>,
    private val callRepository: CallRepository,
    private val endCallListener: EndCallResultListener,
    private val shouldAskCallFeedback: ShouldAskCallFeedbackUseCase,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : EndCallUseCase {

    /**
     * @param conversationId the id of the conversation for the call should be ended.
     */
    override suspend operator fun invoke(conversationId: ConversationId) = withContext(dispatchers.default) {
        val endedCall = callRepository.callsFlow().first().find {
            // This use case can be invoked while joining the call or when the call is established.
            it.conversationId == conversationId && it.status in listOf(
                CallStatus.STARTED,
                CallStatus.INCOMING,
                CallStatus.STILL_ONGOING,
                CallStatus.ESTABLISHED
            )
        }?.let {
            if (it.conversationType == Conversation.Type.GROUP) {
                callingLogger.d("[EndCallUseCase] -> Updating call status to CLOSED_INTERNALLY")
                callRepository.updateCallStatusById(conversationId, CallStatus.CLOSED_INTERNALLY)
            } else {
                callingLogger.d("[EndCallUseCase] -> Updating call status to CLOSED")
                callRepository.updateCallStatusById(conversationId, CallStatus.CLOSED)
            }
            it
        }

        callManager.value.endCall(conversationId)
        callRepository.updateIsCameraOnById(conversationId, false)
        endCallListener.onCallEndedAskForFeedback(shouldAskCallFeedback(endedCall?.establishedTime))
    }
}

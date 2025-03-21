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

import com.wire.kalium.common.logger.callingLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * This use case will reject a call for the given conversation.
 */
class RejectCallUseCase(
    private val callManager: Lazy<CallManager>,
    private val callRepository: CallRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) {

    suspend operator fun invoke(conversationId: ConversationId) = withContext(dispatchers.default) {
        callingLogger.d("[RejectCallUseCase] -> Updating call status to REJECTED")
        callRepository.updateCallStatusById(conversationId, CallStatus.REJECTED)

        callManager.value.rejectCall(conversationId)
    }
}

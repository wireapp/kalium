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

package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.callbacks.AnsweredCallHandler
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.feature.call.CallStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// TODO(testing): create unit test
class OnAnsweredCall(
    private val callRepository: CallRepository,
    private val scope: CoroutineScope,
    private val qualifiedIdMapper: QualifiedIdMapper
) : AnsweredCallHandler {
    override fun onAnsweredCall(remoteConversationIdString: String, arg: Pointer?) {
        callingLogger.i("[OnAnsweredCall] -> ConversationId: $remoteConversationIdString")
        val conversationIdWithDomain = qualifiedIdMapper.fromStringToQualifiedID(remoteConversationIdString)

        scope.launch {
            callRepository.updateCallStatusById(
                conversationIdString = conversationIdWithDomain.toString(),
                status = CallStatus.ANSWERED
            )
        }
    }
}

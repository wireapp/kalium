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
import com.wire.kalium.calling.callbacks.IncomingCallHandler
import com.wire.kalium.calling.types.Uint32_t
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CALL_SUBCONVERSATION_ID
import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.ConversationType
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.feature.call.CallStatus
import com.wire.kalium.logic.feature.call.usecase.LeaveStaleMlsConferenceUseCase
import com.wire.kalium.logic.feature.conversation.LeaveSubconversationUseCase
import com.wire.kalium.logic.functional.onFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// TODO(testing): create unit test
class OnIncomingCall(
    private val callRepository: CallRepository,
    private val callMapper: CallMapper,
    private val qualifiedIdMapper: QualifiedIdMapper,
    private val scope: CoroutineScope
) : IncomingCallHandler {
    override fun onIncomingCall(
        conversationId: String,
        messageTime: Uint32_t,
        userId: String,
        clientId: String,
        isVideoCall: Boolean,
        shouldRing: Boolean,
        conversationType: Int,
        arg: Pointer?
    ) {
        callingLogger.i(
            "[OnIncomingCall] -> ConversationId: ${conversationId.obfuscateId()}" +
                    " | UserId: ${userId.obfuscateId()} | shouldRing: $shouldRing"
        )
        val mappedConversationType = callMapper.fromIntToConversationType(conversationType)
        val isMuted = mappedConversationType == ConversationType.Conference
        val status = if (shouldRing) CallStatus.INCOMING else CallStatus.STILL_ONGOING
        val qualifiedConversationId = qualifiedIdMapper.fromStringToQualifiedID(conversationId)
        scope.launch {
            callRepository.createCall(
                conversationId = qualifiedConversationId,
                status = status,
                callerId = qualifiedIdMapper.fromStringToQualifiedID(userId).toString(),
                isMuted = isMuted,
                isCameraOn = isVideoCall
            )

            if (status == CallStatus.STILL_ONGOING && mappedConversationType == ConversationType.ConferenceMls) {
                callingLogger.i("MLS conference is still on going, try to leave if we are still a member")
                callRepository.leaveMlsConference(qualifiedConversationId)
            }
        }
    }
}

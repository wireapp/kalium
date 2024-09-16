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

package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.CallClosedReason
import com.wire.kalium.calling.callbacks.CloseCallHandler
import com.wire.kalium.calling.types.Uint32_t
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Suppress("LongParameterList")
class OnCloseCall(
    private val callRepository: CallRepository,
    private val scope: CoroutineScope,
    private val qualifiedIdMapper: QualifiedIdMapper
) : CloseCallHandler {
    override fun onClosedCall(
        reason: Int,
        conversationId: String,
        messageTime: Uint32_t,
        userId: String,
        clientId: String?,
        arg: Pointer?
    ) {
        callingLogger.i(
            "[OnCloseCall] -> ConversationId: ${conversationId.obfuscateId()} |" +
                    " UserId: ${userId.obfuscateId()} | Reason: $reason"
        )

        val avsReason = CallClosedReason.fromInt(value = reason)

        val callStatus = getCallStatusFromCloseReason(avsReason)
        val conversationIdWithDomain = qualifiedIdMapper.fromStringToQualifiedID(conversationId)

        scope.launch {

            if (shouldPersistMissedCall(conversationIdWithDomain, callStatus)) {
                callRepository.persistMissedCall(conversationIdWithDomain)
            }

            callRepository.updateCallStatusById(
                conversationId = conversationIdWithDomain,
                status = callStatus
            )

            if (callRepository.getCallMetadataProfile()[conversationIdWithDomain]?.protocol is Conversation.ProtocolInfo.MLS) {
                callRepository.leaveMlsConference(conversationIdWithDomain)
            }

            callingLogger.i("[OnCloseCall] -> ConversationId: ${conversationId.obfuscateId()} | callStatus: $callStatus")
        }
    }

    private fun shouldPersistMissedCall(
        conversationId: ConversationId,
        callStatus: CallStatus
    ): Boolean {
        if (callStatus == CallStatus.MISSED)
            return true
        return callRepository.getCallMetadataProfile().data[conversationId]?.let {
            val isGroupCall = it.conversationType == Conversation.Type.GROUP
            (callStatus == CallStatus.CLOSED &&
                    isGroupCall &&
                    it.establishedTime.isNullOrEmpty() &&
                    it.callStatus != CallStatus.CLOSED_INTERNALLY)
        } ?: false
    }

    private fun getCallStatusFromCloseReason(reason: CallClosedReason): CallStatus = when (reason) {
        CallClosedReason.STILL_ONGOING -> CallStatus.STILL_ONGOING
        CallClosedReason.CANCELLED -> CallStatus.MISSED
        CallClosedReason.TIMEOUT_ECONN -> CallStatus.MISSED
        CallClosedReason.REJECTED -> CallStatus.REJECTED
        else -> {
            CallStatus.CLOSED
        }
    }

}

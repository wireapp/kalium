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

import com.wire.kalium.calling.AvsSendRequestHandler
import com.wire.kalium.calling.callbacks.SendHandler
import com.wire.kalium.common.logger.callingLogger
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.call.CallClientList
import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.feature.call.AvsCallBackError
import com.wire.kalium.messaging.sending.MessageTarget
import kotlinx.serialization.json.Json

@Suppress("FunctionNaming", "LongParameterList")
internal fun OnSendOTR(
    qualifiedIdMapper: QualifiedIdMapper,
    selfUserId: String,
    selfClientId: String,
    callMapper: CallMapper,
    callingMessageSender: CallingMessageSender,
): SendHandler = AvsSendRequestHandler(
        matchesSelf = { userId, clientId -> selfUserId == userId && selfClientId == clientId },
        acceptsPayload = { _, _, _ -> true },
        onRequest = { request ->
            callingLogger.i("[OnSendOTR] -> ConversationId: ${request.remoteConversationId.obfuscateId()}")
            val messageTarget = if (request.myClientsOnly) {
                callingLogger.i("[OnSendOTR] -> Route calling message via self conversation")
                CallingMessageTarget.Self
            } else {
                val specificTarget = request.recipientsJson?.let { recipientsJson ->
                    callMapper.toClientMessageTarget(Json.decodeFromString<CallClientList>(recipientsJson))
                } ?: MessageTarget.Conversation()
                CallingMessageTarget.HostConversation(specificTarget)
            }
            callingMessageSender.enqueueSendingOfCallingMessage(
                context = request.context,
                callHostConversationId = qualifiedIdMapper.fromStringToQualifiedID(request.remoteConversationId),
                messageString = request.content,
                avsSelfUserId = qualifiedIdMapper.fromStringToQualifiedID(request.remoteSelfUserId),
                avsSelfClientId = ClientId(request.remoteSelfClientId),
                messageTarget = messageTarget,
            )
        },
        onFailure = { failure -> callingLogger.e("[OnSendOTR] -> Error", failure) },
        acceptedResult = AvsCallBackError.NONE.value,
        invalidArgumentResult = AvsCallBackError.INVALID_ARGUMENT.value,
        decodingFailureResult = AvsCallBackError.COULD_NOT_DECODE_ARGUMENT.value,
    )

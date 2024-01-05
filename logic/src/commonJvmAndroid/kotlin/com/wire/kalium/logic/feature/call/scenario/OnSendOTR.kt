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
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.callbacks.SendHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.calling.types.Size_t
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallClientList
import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.feature.call.AvsCallBackError
import com.wire.kalium.logic.feature.call.CallManagerImpl
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.data.message.MessageTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.serialization.json.Json

// TODO(testing): create unit test
@Suppress("LongParameterList")
internal class OnSendOTR(
    private val handle: Deferred<Handle>,
    private val calling: Calling,
    private val qualifiedIdMapper: QualifiedIdMapper,
    private val selfUserId: String,
    private val selfClientId: String,
    private val messageSender: MessageSender,
    private val selfConversationIdProvider: SelfConversationIdProvider,
    private val callingScope: CoroutineScope,
    private val callMapper: CallMapper
) : SendHandler {
    @Suppress("TooGenericExceptionCaught", "NestedBlockDepth")
    override fun onSend(
        context: Pointer?,
        remoteConversationId: String,
        remoteSelfUserId: String,
        remoteClientIdSelf: String,
        targetRecipientsJson: String?,
        clientIdDestination: String?,
        data: Pointer?,
        length: Size_t,
        isTransient: Boolean,
        myClientsOnly: Boolean,
        arg: Pointer?
    ): Int {
        callingLogger.i("[OnSendOTR] -> ConversationId: $remoteConversationId")
        return if (selfUserId != remoteSelfUserId && selfClientId != remoteClientIdSelf) {
            callingLogger.i("[OnSendOTR] -> selfUserId: $selfUserId != userIdSelf: $remoteSelfUserId")
            callingLogger.i("[OnSendOTR] -> selfClientId: $selfClientId != clientIdSelf: $remoteClientIdSelf")
            AvsCallBackError.INVALID_ARGUMENT.value
        } else {
            try {
                val messageTarget = if (myClientsOnly) {
                    callingLogger.i("[OnSendOTR] -> Route calling message via self conversation")
                    MessageTarget.Conversation()
                } else {
                    callingLogger.i("[OnSendOTR] -> Decoding Recipients")
                    targetRecipientsJson?.let { recipientsJson ->
                        val callClientList = Json.decodeFromString<CallClientList>(recipientsJson)

                        callingLogger.i("[OnSendOTR] -> Mapping Recipients")
                        callMapper.toClientMessageTarget(callClientList = callClientList)
                    } ?: MessageTarget.Conversation()
                }

                callingLogger.i("[OnSendOTR] -> Success")
                OnHttpRequest(handle, calling, messageSender, callingScope, selfConversationIdProvider).sendHandlerSuccess(
                    context = context,
                    messageString = data?.getString(0, CallManagerImpl.UTF8_ENCODING),
                    conversationId = qualifiedIdMapper.fromStringToQualifiedID(remoteConversationId),
                    avsSelfUserId = qualifiedIdMapper.fromStringToQualifiedID(remoteSelfUserId),
                    avsSelfClientId = ClientId(remoteClientIdSelf),
                    messageTarget = messageTarget,
                    sendInSelfConversation = myClientsOnly
                )
                AvsCallBackError.NONE.value
            } catch (exception: Exception) {
                callingLogger.e("[OnSendOTR] -> Error Exception: $exception")
                AvsCallBackError.COULD_NOT_DECODE_ARGUMENT.value
            }
        }
    }
}

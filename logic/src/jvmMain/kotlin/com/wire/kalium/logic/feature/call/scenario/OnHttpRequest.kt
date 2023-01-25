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

import com.benasher44.uuid.uuid4
import com.sun.jna.Pointer
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.feature.message.MessageTarget
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch

// TODO(testing): create unit test
class OnHttpRequest(
    private val handle: Deferred<Handle>,
    private val calling: Calling,
    private val messageSender: MessageSender,
    private val callingScope: CoroutineScope
) {
    @Suppress("LongParameterList")
    fun sendHandlerSuccess(
        context: Pointer?,
        messageString: String?,
        conversationId: ConversationId,
        avsSelfUserId: UserId,
        avsSelfClientId: ClientId,
        messageTarget: MessageTarget
    ) {
        callingScope.launch {
            messageString?.let { message ->
                when (sendCallingMessage(conversationId, avsSelfUserId, avsSelfClientId, message, messageTarget)) {
                    is Either.Right -> {
                        callingLogger.i("[OnHttpRequest] -> Success")
                        calling.wcall_resp(
                            inst = handle.await(),
                            status = 200,
                            reason = "",
                            arg = context
                        )
                    }

                    is Either.Left -> {
                        callingLogger.i("[OnHttpRequest] -> Error")
                        calling.wcall_resp(
                            inst = handle.await(),
                            status = 400, // TODO(calling): Handle the errorCode from CoreFailure
                            reason = "Couldn't send Calling Message",
                            arg = context
                        )
                    }
                }
            }
        }
    }

    private suspend fun sendCallingMessage(
        conversationId: ConversationId,
        userId: UserId,
        clientId: ClientId,
        data: String,
        messageTarget: MessageTarget
    ): Either<CoreFailure, Unit> {
        val messageContent = MessageContent.Calling(data)
        val date = DateTimeUtil.currentIsoDateTimeString()
        val message = Message.Signaling(
            id = uuid4().toString(),
            content = messageContent,
            conversationId = conversationId,
            date = date,
            senderUserId = userId,
            senderClientId = clientId,
            status = Message.Status.SENT,
        )
        return messageSender.sendMessage(message, messageTarget)
    }
}

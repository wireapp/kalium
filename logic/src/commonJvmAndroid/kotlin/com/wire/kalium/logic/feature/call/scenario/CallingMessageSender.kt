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

import com.benasher44.uuid.uuid4
import com.sun.jna.Pointer
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageTarget
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import io.ktor.http.HttpStatusCode
import io.mockative.Mockable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

@Mockable
internal interface CallingMessageSender {

    suspend fun processQueue()

    @Suppress("LongParameterList")
    fun enqueueSendingOfCallingMessage(
        context: Pointer?,
        callHostConversationId: ConversationId,
        messageString: String?,
        avsSelfUserId: UserId,
        avsSelfClientId: ClientId,
        messageTarget: CallingMessageTarget,
    )
}

@Suppress("FunctionNaming")
internal fun CallingMessageSender(
    handle: Deferred<Handle>,
    calling: Calling,
    messageSender: MessageSender,
    callingScope: CoroutineScope,
    selfConversationIdProvider: SelfConversationIdProvider
) = object : CallingMessageSender {

    private val logger = callingLogger.withTextTag("CallingMessageSender")

    private val queue = Channel<CallingMessageInstructions>(
        capacity = Channel.UNLIMITED,
    )

    @Suppress("LongParameterList")
    override fun enqueueSendingOfCallingMessage(
        context: Pointer?,
        callHostConversationId: ConversationId,
        messageString: String?,
        avsSelfUserId: UserId,
        avsSelfClientId: ClientId,
        messageTarget: CallingMessageTarget,
    ) {
        if (messageString == null) return
        callingScope.launch {
            queue.send(
                CallingMessageInstructions(
                    context,
                    callHostConversationId,
                    messageString,
                    avsSelfUserId,
                    avsSelfClientId,
                    messageTarget,
                )
            )
        }
    }

    override suspend fun processQueue() {
        queue.consumeAsFlow().collect { messageInstructions ->
            processInstruction(messageInstructions, selfConversationIdProvider)
        }
    }

    private suspend fun processInstruction(
        messageInstructions: CallingMessageInstructions,
        selfConversationIdProvider: SelfConversationIdProvider
    ) {
        val target = messageInstructions.messageTarget

        val transportConversationIds = when (target) {
            is CallingMessageTarget.Self -> {
                selfConversationIdProvider()
            }

            is CallingMessageTarget.HostConversation -> {
                Either.Right(listOf(messageInstructions.callHostConversationId))
            }
        }

        val result = transportConversationIds.flatMap { conversations ->
            conversations.foldToEitherWhileRight(Unit) { transportConversationId, _ ->
                sendCallingMessage(
                    messageInstructions.callHostConversationId,
                    messageInstructions.avsSelfUserId,
                    messageInstructions.avsSelfClientId,
                    messageInstructions.messageString,
                    target.specificTarget,
                    transportConversationId
                )
            }
        }

        val (code, message) = when (result) {
            is Either.Right -> {
                logger.i("Notifying AVS - Success sending message")
                HttpStatusCode.OK.value to ""
            }

            is Either.Left -> {
                logger.i("Notifying AVS - Error sending message")
                HttpStatusCode.BadRequest.value to "Couldn't send Calling Message"
            }
        }
        calling.wcall_resp(
            inst = handle.await(),
            status = code,
            reason = message,
            arg = messageInstructions.context
        )
    }

    @Suppress("LongParameterList")
    private suspend fun sendCallingMessage(
        callHostConversationId: ConversationId,
        userId: UserId,
        clientId: ClientId,
        data: String,
        messageTarget: MessageTarget,
        transportConversationId: ConversationId
    ): Either<CoreFailure, Unit> {
        val messageContent = MessageContent.Calling(data, callHostConversationId)
        val date = Clock.System.now()
        val message = Message.Signaling(
            id = uuid4().toString(),
            content = messageContent,
            conversationId = transportConversationId,
            date = date,
            senderUserId = userId,
            senderClientId = clientId,
            status = Message.Status.Sent,
            isSelfMessage = true,
            expirationData = null
        )
        return messageSender.sendMessage(message, messageTarget)
    }
}

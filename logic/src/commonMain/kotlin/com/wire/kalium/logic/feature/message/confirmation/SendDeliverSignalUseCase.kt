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
package com.wire.kalium.logic.feature.message.confirmation

import com.benasher44.uuid.uuid4
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.MessageId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.logStructuredJson
import kotlinx.datetime.Clock

/**
 * Use case for sending a delivery confirmation signal for a list of messages in a conversation.
 */
interface SendDeliverSignalUseCase {
    suspend operator fun invoke(conversation: Conversation, messages: List<MessageId>): Either<CoreFailure, Unit>
}

internal class SendDeliverSignalUseCaseImpl(
    private val selfUserId: UserId,
    private val messageSender: MessageSender,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val kaliumLogger: KaliumLogger
) : SendDeliverSignalUseCase {
    override suspend fun invoke(
        conversation: Conversation,
        messages: List<MessageId>
    ): Either<CoreFailure, Unit> = currentClientIdProvider()
        .flatMap { currentClientId ->
            val message = Message.Signaling(
                id = uuid4().toString(),
                content = MessageContent.Receipt(ReceiptType.DELIVERED, messages),
                conversationId = conversation.id,
                date = Clock.System.now(),
                senderUserId = selfUserId,
                senderClientId = currentClientId,
                status = Message.Status.Pending,
                isSelfMessage = true,
                expirationData = null
            )
            messageSender.sendMessage(message)
                .onFailure { error ->
                    kaliumLogger.logStructuredJson(
                        level = KaliumLogLevel.ERROR,
                        leadingMessage = "Error while sending delivery confirmation for ${conversation.id.toLogString()}",
                        jsonStringKeyValues = mapOf(
                            "conversationId" to conversation.id.toLogString(),
                            "messages" to messages.joinToString { it.obfuscateId() },
                            "error" to error.toString()
                        )
                    )
                }
                .onSuccess {
                    kaliumLogger.logStructuredJson(
                        level = KaliumLogLevel.DEBUG,
                        leadingMessage = "Delivery confirmation sent for ${conversation.id.toLogString()}" +
                                " and message count: ${messages.size}",
                        jsonStringKeyValues = mapOf(
                            "conversationId" to conversation.id.toLogString(),
                            "messages" to messages.joinToString { it.obfuscateId() },
                            "messageCount" to messages.size
                        )
                    )
                }
        }
}

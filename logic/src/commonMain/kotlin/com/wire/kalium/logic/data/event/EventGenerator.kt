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
package com.wire.kalium.logic.data.event

import com.benasher44.uuid.uuid4
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.functional.right
import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.ProteusCoreCryptoContext
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedClientID
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.authenticated.notification.EventResponse
import com.wire.kalium.network.api.authenticated.notification.conversation.MessageEventData
import io.ktor.util.encodeBase64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock

class EventGenerator(
    private val selfClient: QualifiedClientID,
    targetClient: QualifiedClientID
) {

    private val protoContentMapper: ProtoContentMapper = MapperProvider.protoContentMapper(selfClient.userId)
    private val targetSessionId = CryptoSessionId(
        targetClient.userId.toCrypto(),
        CryptoClientId(targetClient.clientId.value)
    )
    private val selfSessionId = CryptoSessionId(
        selfClient.userId.toCrypto(),
        CryptoClientId(selfClient.clientId.value)
    )

    fun generateEvents(
        transactionProvider: CryptoTransactionProvider,
        limit: Int,
        conversationId: ConversationId,
    ): Flow<EventResponse> {
        return flow {
            repeat(limit) { count ->
                val protobuf = generateProtoContent(generateTextContent(count))
                val message = transactionProvider.proteusTransaction("EventGenerator") {
                    encryptMessage(protobuf, it, selfSessionId, targetSessionId).right()
                }
                message.onSuccess {
                    val event = generateNewMessageDTO(selfClient.userId, conversationId, it)
                    emit(generateEventResponse(event))
                }
            }
        }
    }

    private fun generateTextContent(
        index: Int
    ): MessageContent.Text {
        return MessageContent.Text("Message $index")
    }

    private fun generateProtoContent(
        messageContent: MessageContent.FromProto
    ): PlainMessageBlob {
        return protoContentMapper.encodeToProtobuf(
            ProtoContent.Readable(
                messageUid = uuid4().toString(),
                messageContent = messageContent,
                expectsReadConfirmation = true,
                legalHoldStatus = Conversation.LegalHoldStatus.DISABLED,
                expiresAfterMillis = null
            )
        )
    }

    private suspend fun encryptMessage(
        message: PlainMessageBlob,
        proteusContext: ProteusCoreCryptoContext,
        sender: CryptoSessionId,
        recipient: CryptoSessionId
    ): MessageEventData {
        return MessageEventData(
            text = proteusContext.encrypt(message.data, recipient).encodeBase64(),
            sender = sender.cryptoClientId.value,
            recipient = recipient.cryptoClientId.value,
            encryptedExternalData = null
        )
    }

    private fun generateNewMessageDTO(
        from: UserId,
        conversationId: ConversationId,
        data: MessageEventData
    ): EventContentDTO.Conversation.NewMessageDTO {
        return EventContentDTO.Conversation.NewMessageDTO(
            qualifiedConversation = conversationId.toApi(),
            qualifiedFrom = from.toApi(),
            conversation = conversationId.toPlainID().value,
            from = from.toPlainID().value,
            time = Clock.System.now(),
            data = data
        )

    }

    private fun generateEventResponse(event: EventContentDTO): EventResponse {
        return EventResponse(
            id = uuid4().toString(), // TODO jacob (should actually be UUIDv1)
            payload = listOf(event),
            transient = true // All events are transient to avoid persisting an incorrect last event id
        )
    }

}

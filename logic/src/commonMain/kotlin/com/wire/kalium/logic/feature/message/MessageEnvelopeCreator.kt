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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.utils.PlainData
import com.wire.kalium.cryptography.utils.calcSHA256
import com.wire.kalium.cryptography.utils.encryptDataWithAES256
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.MESSAGES
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.BroadcastMessage
import com.wire.kalium.logic.data.message.ClientPayload
import com.wire.kalium.logic.data.message.EncryptedMessageBlob
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.data.message.MessageEnvelope
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.message.RecipientEntry
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.data.client.ProteusClientProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.LegalHoldStatusMapper
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.common.error.wrapProteusRequest
import kotlinx.coroutines.flow.first

interface MessageEnvelopeCreator {

    suspend fun createOutgoingEnvelope(
        recipients: List<Recipient>,
        message: Message.Sendable
    ): Either<CoreFailure, MessageEnvelope>

    suspend fun createOutgoingBroadcastEnvelope(
        recipients: List<Recipient>,
        message: BroadcastMessage
    ): Either<CoreFailure, MessageEnvelope>

}

class MessageEnvelopeCreatorImpl(
    private val conversationRepository: ConversationRepository,
    private val legalHoldStatusMapper: LegalHoldStatusMapper,
    private val proteusClientProvider: ProteusClientProvider,
    private val selfUserId: UserId,
    private val protoContentMapper: ProtoContentMapper = MapperProvider.protoContentMapper(selfUserId = selfUserId),
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : MessageEnvelopeCreator {

    override suspend fun createOutgoingEnvelope(
        recipients: List<Recipient>,
        message: Message.Sendable
    ): Either<CoreFailure, MessageEnvelope> {
        val senderClientId = message.senderClientId

        val expectsReadConfirmation = when (message) {
            is Message.Regular -> message.expectsReadConfirmation
            else -> false
        }

        val legalHoldStatus = conversationRepository.observeLegalHoldStatus(
            message.conversationId
        ).first().let {
            legalHoldStatusMapper.mapLegalHoldConversationStatus(it, message)
        }

        val actualMessageContent = ProtoContent.Readable(
            messageUid = message.id,
            messageContent = message.content,
            expectsReadConfirmation = expectsReadConfirmation,
            expiresAfterMillis = message.expirationData?.expireAfter?.inWholeMilliseconds,
            legalHoldStatus = legalHoldStatus
        )

        return createEnvelope(actualMessageContent, recipients, senderClientId)
    }

    override suspend fun createOutgoingBroadcastEnvelope(
        recipients: List<Recipient>,
        message: BroadcastMessage
    ): Either<CoreFailure, MessageEnvelope> {
        val senderClientId = message.senderClientId
        val expectsReadConfirmation = false

        val legalHoldStatus = Conversation.LegalHoldStatus.UNKNOWN

        val actualMessageContent = ProtoContent.Readable(message.id, message.content, expectsReadConfirmation, legalHoldStatus)

        return createEnvelope(actualMessageContent, recipients, senderClientId)
    }

    private suspend fun createEnvelope(
        actualMessageContent: ProtoContent.Readable,
        recipients: List<Recipient>,
        senderClientId: ClientId
    ): Either<CoreFailure, MessageEnvelope> {
        val (encodedContent, externalDataBlob) = getContentAndExternalData(actualMessageContent, recipients)

        val sessions = recipients.flatMap { recipient ->
            recipient.clients.map { client ->
                CryptoSessionId(idMapper.toCryptoQualifiedIDId(recipient.id), CryptoClientId(client.value))
            }
        }

        return proteusClientProvider.getOrError().flatMap { proteusClient ->
            wrapProteusRequest {
                proteusClient.encryptBatched(encodedContent.data, sessions)
            }
        }.flatMap {
            val recipientEntries = it.entries.groupBy(
                { it.key.userId.toModel() },
                { ClientPayload(it.key.cryptoClientId.toModel(), EncryptedMessageBlob(it.value)) }
            ).map { RecipientEntry(it.key, it.value) }
            Either.Right(MessageEnvelope(senderClientId, recipientEntries, externalDataBlob))
        }
    }

    private fun getContentAndExternalData(
        actualMessageContent: ProtoContent.Readable,
        recipients: List<Recipient>
    ): Pair<PlainMessageBlob, EncryptedMessageBlob?> {
        val encodedContent = protoContentMapper.encodeToProtobuf(actualMessageContent)

        val encryptedMessageSizeEstimate = encodedContent.data.size + ENCRYPTED_MESSAGE_OVERHEAD
        val totalClients = recipients.sumOf { recipient -> recipient.clients.size }

        val totalEstimatedSize = encryptedMessageSizeEstimate * totalClients

        return if (totalEstimatedSize <= MAX_CONTENT_SIZE) {
            kaliumLogger.withFeatureId(MESSAGES).v("External message is not needed")
            encodedContent to null
        } else {
            kaliumLogger.withFeatureId(MESSAGES).v("Creating external message")
            val otrKey = generateRandomAES256Key()
            val encryptedExternalBlob = encryptDataWithAES256(PlainData(encodedContent.data), otrKey)
            val contentHash = calcSHA256(encryptedExternalBlob.data)
            val externalInstructions = protoContentMapper.encodeToProtobuf(
                ProtoContent.ExternalMessageInstructions(
                    actualMessageContent.messageUid,
                    otrKey.data,
                    contentHash,
                    MessageEncryptionAlgorithm.AES_CBC
                )
            )
            externalInstructions to EncryptedMessageBlob(encryptedExternalBlob.data)
        }
    }

    internal companion object {
        /**
         * An encrypted Proteus message has extra information about the encryption itself.
         * The content after encryption is around 190 bytes larger than the original
         * when encrypting the first ever message to a client.
         * Can be smaller (around 110 bytes) after the other client replies for the first time.
         * In order to account the overhead added by each client and user, and to  add a safety margin
         */
        const val ENCRYPTED_MESSAGE_OVERHEAD = 256

        /**
         * Maximum size of the messages payload accepted by the servers without [ProtoContent.ExternalMessageInstructions].
         */
        const val MAX_CONTENT_SIZE = 200 * 1024
    }
}

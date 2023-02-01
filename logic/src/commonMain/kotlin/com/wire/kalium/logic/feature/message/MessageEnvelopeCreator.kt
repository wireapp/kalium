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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.cryptography.utils.PlainData
import com.wire.kalium.cryptography.utils.calcSHA256
import com.wire.kalium.cryptography.utils.encryptDataWithAES256
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.MESSAGES
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.ProteusFailure
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.IdMapper
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
import com.wire.kalium.logic.feature.ProteusClientProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapCryptoRequest

interface MessageEnvelopeCreator {

    suspend fun createOutgoingEnvelope(
        recipients: List<Recipient>,
        message: Message.Sendable
    ): Either<CoreFailure, MessageEnvelope>

}

class MessageEnvelopeCreatorImpl(
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

        val actualMessageContent = ProtoContent.Readable(message.id, message.content, expectsReadConfirmation)
        val (encodedContent, externalDataBlob) = getContentAndExternalData(actualMessageContent, recipients)

        return recipients.foldToEitherWhileRight(mutableListOf<RecipientEntry>()) { recipient, recipientAccumulator ->
            recipient.clients.foldToEitherWhileRight(mutableListOf<ClientPayload>()) { client, clientAccumulator ->
                val session = CryptoSessionId(idMapper.toCryptoQualifiedIDId(recipient.id), CryptoClientId(client.value))

                proteusClientProvider.getOrError()
                    .flatMap { proteusClient ->
                        wrapCryptoRequest {
                            proteusClient.encrypt(encodedContent.data, session)
                        }
                    }
                    .map { EncryptedMessageBlob(it) }
                    .fold({
                        // when encryption fails because of SESSION_NOT_FOUND, we just skip the client
                        // the reason is that the client might be buggy from the backend side and have no preKey
                        // in that case we just skip the client and send the message to the rest of the clients
                        // the only valid way to fitch client pryKey if the server response that we are missing clients
                        if (it is ProteusFailure && it.proteusException.code == ProteusException.Code.SESSION_NOT_FOUND) {
                            Either.Right(clientAccumulator)
                        } else {
                            Either.Left(it)
                        }
                    }, { encryptedContent ->
                        Either.Right(clientAccumulator.also {
                            it.add(ClientPayload(client, encryptedContent))
                            kaliumLogger.d("Encrypted message size: ${encryptedContent.data.size}")
                        })
                    })
            }
                .map { clientEntries ->
                    recipientAccumulator.also {
                        it.add(RecipientEntry(recipient.id, clientEntries))
                    }
                }
        }
            .map { recipientEntries ->
                MessageEnvelope(senderClientId, recipientEntries, externalDataBlob)
            }
    }

    private fun getContentAndExternalData(
        actualMessageContent: ProtoContent.Readable,
        recipients: List<Recipient>
    ): Pair<PlainMessageBlob, EncryptedMessageBlob?> {
        val encodedContent = protoContentMapper.encodeToProtobuf(actualMessageContent)

        val encryptedMessageSizeEstimate = encodedContent.data.size + ENCRYPTED_MESSAGE_OVERHEAD
        val totalClients = recipients.sumOf { recipient -> recipient.clients.size }

        val totalEstimatedSize = encryptedMessageSizeEstimate + totalClients
        kaliumLogger.withFeatureId(MESSAGES).v(
            "Original message size: ${encodedContent.data.size}; " +
                    "Estimated total message size = $totalEstimatedSize, " +
                    "for $totalClients clients"
        )

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
        const val MAX_CONTENT_SIZE = 256 * 1024
    }
}

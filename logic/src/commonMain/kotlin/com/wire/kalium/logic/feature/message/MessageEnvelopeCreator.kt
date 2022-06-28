package com.wire.kalium.logic.feature.message

import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.utils.PlainData
import com.wire.kalium.cryptography.utils.calcSHA256
import com.wire.kalium.cryptography.utils.encryptDataWithAES256
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.logic.CoreFailure
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
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapCryptoRequest

interface MessageEnvelopeCreator {

    suspend fun createOutgoingEnvelope(
        recipients: List<Recipient>,
        message: Message.Regular
    ): Either<CoreFailure, MessageEnvelope>

}

class MessageEnvelopeCreatorImpl(
    private val proteusClient: ProteusClient,
    private val protoContentMapper: ProtoContentMapper = MapperProvider.protoContentMapper(),
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : MessageEnvelopeCreator {

    override suspend fun createOutgoingEnvelope(
        recipients: List<Recipient>,
        message: Message.Regular
    ): Either<CoreFailure, MessageEnvelope> {
        val senderClientId = message.senderClientId
        ProtoContent.Readable(message.id, message.content)

        val actualMessageContent = ProtoContent.Readable(message.id, message.content)
        val (encodedContent, externalDataBlob) = getContentAndExternalData(actualMessageContent, recipients)

        return recipients.foldToEitherWhileRight(mutableListOf<RecipientEntry>()) { recipient, recipientAccumulator ->
            recipient.clients.foldToEitherWhileRight(mutableListOf<ClientPayload>()) { client, clientAccumulator ->
                val session = CryptoSessionId(idMapper.toCryptoQualifiedIDId(recipient.member.id), CryptoClientId(client.value))

                wrapCryptoRequest { EncryptedMessageBlob(proteusClient.encrypt(encodedContent.data, session)) }
                    .map { encryptedContent ->
                        clientAccumulator.also {
                            it.add(ClientPayload(client, encryptedContent))
                            kaliumLogger.d("Encrypted message size: ${encryptedContent.data.size}")
                        }
                    }
            }.map { clientEntries ->
                recipientAccumulator.also {
                    it.add(RecipientEntry(recipient.member.id, clientEntries))
                }
            }
        }.map { recipientEntries ->
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
        kaliumLogger.v(
            "Original message size: ${encodedContent.data.size}; " +
                    "Estimated total message size = $totalEstimatedSize, " +
                    "for $totalClients clients"
        )

        return if (totalEstimatedSize <= MAX_CONTENT_SIZE) {
            kaliumLogger.v("External message is not needed")
            encodedContent to null
        } else {
            kaliumLogger.v("Creating external message")
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

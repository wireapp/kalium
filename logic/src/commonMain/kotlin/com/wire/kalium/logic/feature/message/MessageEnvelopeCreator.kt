package com.wire.kalium.logic.feature.message

import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.message.ClientPayload
import com.wire.kalium.logic.data.message.EncryptedMessageBlob
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageEnvelope
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.message.RecipientEntry
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.cryptography.UserId as CryptoUserId

interface MessageEnvelopeCreator {

    suspend fun createOutgoingEnvelope(
        recipients: List<Recipient>,
        message: Message
    ): Either<CoreFailure, MessageEnvelope>

}

class MessageEnvelopeCreatorImpl(
    private val proteusClient: ProteusClient,
    private val protoContentMapper: ProtoContentMapper
) : MessageEnvelopeCreator {

    override suspend fun createOutgoingEnvelope(
        recipients: List<Recipient>,
        message: Message
    ): Either<CoreFailure, MessageEnvelope> = suspending {
        val senderClientId = message.senderClientId
        val content = protoContentMapper.encodeToProtobuf(ProtoContent(message.id, message.content))

        recipients.foldToEitherWhileRight(mutableListOf<RecipientEntry>()) { recipient, recipientAccumulator ->
            recipient.clients.foldToEitherWhileRight(mutableListOf<ClientPayload>()) { client, clientAccumulator ->
                val session = CryptoSessionId(CryptoUserId(recipient.member.id.value), CryptoClientId(client.value))
                try {
                    Either.Right(EncryptedMessageBlob(proteusClient.encrypt(content.data, session)))
                } catch (proteusException: ProteusException) {
                    Either.Left(CoreFailure.Unknown(proteusException))
                }.map { encryptedContent ->
                    clientAccumulator.also {
                        it.add(ClientPayload(client, encryptedContent))
                    }
                }
            }.map { clientEntries ->
                recipientAccumulator.also {
                    it.add(RecipientEntry(recipient.member.id, clientEntries))
                }
            }
        }.map { recipientEntries ->
            MessageEnvelope(senderClientId, recipientEntries)
        }
    }
}

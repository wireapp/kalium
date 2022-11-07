package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.EncryptedData
import com.wire.kalium.cryptography.utils.decryptDataWithAES256
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.ProteusFailure
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.MigratedMessage
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.ProteusClientProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.Base64
import com.wire.kalium.logic.wrapCryptoRequest
import io.ktor.utils.io.core.toByteArray

internal interface ProteusMessageUnpacker {

    suspend fun unpackProteusMessage(event: Event.Conversation.NewMessage): Either<CoreFailure, MessageUnpackResult>
    suspend fun unpackMigratedProteusMessage(migratedMessage: MigratedMessage): Either<CoreFailure, MessageUnpackResult>

}

internal class ProteusMessageUnpackerImpl(
    private val proteusClientProvider: ProteusClientProvider,
    private val protoContentMapper: ProtoContentMapper = MapperProvider.protoContentMapper(),
    private val idMapper: IdMapper = MapperProvider.idMapper(),
) : ProteusMessageUnpacker {

    private val logger get() = kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER)

    override suspend fun unpackProteusMessage(event: Event.Conversation.NewMessage): Either<CoreFailure, MessageUnpackResult> {
        val decodedContentBytes = Base64.decodeFromBase64(event.content.toByteArray())
        val cryptoSessionId = CryptoSessionId(
            idMapper.toCryptoQualifiedIDId(event.senderUserId),
            CryptoClientId(event.senderClientId.value)
        )
        return proteusClientProvider.getOrError()
            .flatMap {
                wrapCryptoRequest {
                    it.decrypt(decodedContentBytes, cryptoSessionId)
                }
            }
            .map { PlainMessageBlob(it) }
            .flatMap { plainMessageBlob -> getReadableMessageContent(plainMessageBlob, event.encryptedExternalContent) }
            .onFailure {
                when (it) {
                    is CoreFailure.Unknown -> logger.e("UnknownFailure when processing message: $it", it.rootCause)
                    is ProteusFailure -> logger.e("ProteusFailure when processing message: $it", it.proteusException)
                    else -> logger.e("Failure when processing message: $it")
                }
            }.map { readableContent ->
                MessageUnpackResult.ApplicationMessage(
                    conversationId = event.conversationId,
                    timestampIso = event.timestampIso,
                    senderUserId = event.senderUserId,
                    senderClientId = event.senderClientId,
                    content = readableContent
                )
            }
    }

    override suspend fun unpackMigratedProteusMessage(migratedMessage: MigratedMessage): Either<CoreFailure, MessageUnpackResult> {
        val decodedContentBytes = Base64.decodeFromBase64(migratedMessage.content.toByteArray())
        val cryptoSessionId = CryptoSessionId(
            idMapper.toCryptoQualifiedIDId(migratedMessage.senderUserId),
            CryptoClientId(migratedMessage.senderClientId.value)
        )
        return proteusClientProvider.getOrError()
            .flatMap {
                wrapCryptoRequest {
                    it.decrypt(decodedContentBytes, cryptoSessionId)
                }
            }
            .map { PlainMessageBlob(it) }
            .flatMap { plainMessageBlob ->
                getReadableMessageContent(plainMessageBlob, migratedMessage.encryptedProto?.let { EncryptedData(it) })
            }
            .onFailure {
                when (it) {
                    is CoreFailure.Unknown -> logger.e("UnknownFailure when processing message: $it", it.rootCause)
                    is ProteusFailure -> logger.e("ProteusFailure when processing message: $it", it.proteusException)
                    else -> logger.e("Failure when processing message: $it")
                }
            }.map { readableContent ->
                MessageUnpackResult.ApplicationMessage(
                    conversationId = migratedMessage.conversationId,
                    timestampIso = migratedMessage.timestampIso,
                    senderUserId = migratedMessage.senderUserId,
                    senderClientId = migratedMessage.senderClientId,
                    content = readableContent
                )
            }
    }

    private fun getReadableMessageContent(
        plainMessageBlob: PlainMessageBlob,
        encryptedData: EncryptedData?
    ) = when (val protoContent = protoContentMapper.decodeFromProtobuf(plainMessageBlob)) {
        is ProtoContent.Readable -> Either.Right(protoContent)
        is ProtoContent.ExternalMessageInstructions -> encryptedData?.let {
            logger.d("Solving external content '$protoContent', EncryptedData='$it'")
            solveExternalContentForProteusMessage(protoContent, encryptedData)
        } ?: run {
            val rootCause = IllegalArgumentException("Null external content when processing external message instructions.")
            Either.Left(CoreFailure.Unknown(rootCause))
        }
    }

    private fun solveExternalContentForProteusMessage(
        externalInstructions: ProtoContent.ExternalMessageInstructions,
        externalData: EncryptedData
    ): Either<CoreFailure, ProtoContent.Readable> = wrapCryptoRequest {
        val decryptedExternalMessage = decryptDataWithAES256(externalData, AES256Key(externalInstructions.otrKey)).data
        logger.d("ExternalMessage - Decrypted external message content: '$decryptedExternalMessage'")
        PlainMessageBlob(decryptedExternalMessage)
    }.map(protoContentMapper::decodeFromProtobuf).flatMap { decodedProtobuf ->
        if (decodedProtobuf !is ProtoContent.Readable) {
            val rootCause = IllegalArgumentException("матрёшка! External message can't contain another external message inside!")
            Either.Left(CoreFailure.Unknown(rootCause))
        } else {
            Either.Right(decodedProtobuf)
        }
    }
}

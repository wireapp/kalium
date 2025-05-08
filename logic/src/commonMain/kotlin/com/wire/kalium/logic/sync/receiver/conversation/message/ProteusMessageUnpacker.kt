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

package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.EncryptedData
import com.wire.kalium.cryptography.utils.decryptDataWithAES256
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.ProteusFailure
import com.wire.kalium.logic.data.client.ProteusClientProvider
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.util.Base64
import com.wire.kalium.common.error.wrapProteusRequest
import io.ktor.utils.io.core.toByteArray
import io.mockative.Mockable

@Mockable
internal interface ProteusMessageUnpacker {

    suspend fun <T : Any> unpackProteusMessage(
        event: Event.Conversation.NewMessage,
        handleMessage: suspend (applicationMessage: MessageUnpackResult.ApplicationMessage) -> T
    ): Either<CoreFailure, T>

}

internal class ProteusMessageUnpackerImpl(
    private val proteusClientProvider: ProteusClientProvider,
    private val selfUserId: UserId,
    private val protoContentMapper: ProtoContentMapper = MapperProvider.protoContentMapper(selfUserId = selfUserId),
    private val idMapper: IdMapper = MapperProvider.idMapper(),
) : ProteusMessageUnpacker {

    private val logger get() = kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER)

    override suspend fun <T : Any> unpackProteusMessage(
        event: Event.Conversation.NewMessage,
        handleMessage: suspend (applicationMessage: MessageUnpackResult.ApplicationMessage) -> T
    ): Either<CoreFailure, T> {
        val decodedContentBytes = Base64.decodeFromBase64(event.content.toByteArray())
        val cryptoSessionId = CryptoSessionId(
            idMapper.toCryptoQualifiedIDId(event.senderUserId),
            CryptoClientId(event.senderClientId.value)
        )
        return proteusClientProvider.getOrError()
            .flatMap {
                wrapProteusRequest {
                    it.decrypt(decodedContentBytes, cryptoSessionId) {
                        val plainMessageBlob = PlainMessageBlob(it)
                        getReadableMessageContent(plainMessageBlob, event.encryptedExternalContent).map { readableContent ->
                            val appMessage = MessageUnpackResult.ApplicationMessage(
                                conversationId = event.conversationId,
                                instant = event.messageInstant,
                                senderUserId = event.senderUserId,
                                senderClientId = event.senderClientId,
                                content = readableContent
                            )
                            handleMessage(appMessage)
                        }
                    }
                }
            }.flatMap { it }
            .onFailure { logUnpackingError(it, event, cryptoSessionId) }
    }

    private fun logUnpackingError(
        it: CoreFailure,
        event: Event.Conversation.NewMessage,
        cryptoSessionId: CryptoSessionId
    ) {
        when (it) {
            is CoreFailure.Unknown -> logger.e("UnknownFailure when processing message: $it", it.rootCause)

            is ProteusFailure -> {
                val loggableException = """
                    {
                      "code": "${it.proteusException.code.name}",
                      "intCode": "${it.proteusException.intCode}",
                      "message": "${it.proteusException.message}",
                      "error": "${it.proteusException.stackTraceToString()}",
                      "senderClientId": "${event.senderClientId.value.obfuscateId()}",
                      "senderUserId": "${event.senderUserId.value.obfuscateId()}",
                      "cryptoClientId": "${cryptoSessionId.cryptoClientId.value.obfuscateId()}",
                      "cryptoUserId": "${cryptoSessionId.userId.value.obfuscateId()}"
                    }
                    """.trimIndent()
                logger.e("ProteusFailure when processing message detail: $loggableException")
            }

            else -> logger.e("Failure when processing message: $it")
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
            val rootCause = IllegalArgumentException(
                "Null external content when processing external message instructions."
            )
            Either.Left(CoreFailure.Unknown(rootCause))
        }
    }

    private fun solveExternalContentForProteusMessage(
        externalInstructions: ProtoContent.ExternalMessageInstructions,
        externalData: EncryptedData
    ): Either<CoreFailure, ProtoContent.Readable> = wrapProteusRequest {
        val decryptedExternalMessage = decryptDataWithAES256(externalData, AES256Key(externalInstructions.otrKey)).data
        logger.d("ExternalMessage - Decrypted external message content: '$decryptedExternalMessage'")
        PlainMessageBlob(decryptedExternalMessage)
    }.map(protoContentMapper::decodeFromProtobuf).flatMap { decodedProtobuf ->
        if (decodedProtobuf !is ProtoContent.Readable) {
            val rootCause = IllegalArgumentException(
                "матрёшка! External message can't contain another external message inside!"
            )
            Either.Left(CoreFailure.Unknown(rootCause))
        } else {
            Either.Right(decodedProtobuf)
        }
    }
}

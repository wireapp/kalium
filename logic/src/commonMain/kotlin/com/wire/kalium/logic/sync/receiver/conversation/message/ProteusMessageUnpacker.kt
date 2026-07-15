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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.ProteusFailure
import com.wire.kalium.common.error.wrapProteusRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.ProteusCoreCryptoContext
import com.wire.kalium.cryptography.utils.EncryptedData
import com.wire.kalium.calling.WireCallingMessageCodec
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import kotlin.io.encoding.Base64

internal interface ProteusMessageUnpacker {

    suspend fun <T : Any> unpackProteusMessage(
        proteusContext: ProteusCoreCryptoContext,
        event: Event.Conversation.NewMessage,
        handleMessage: suspend (applicationMessage: MessageUnpackResult.ApplicationMessage) -> T
    ): Either<CoreFailure, T>

}

internal class ProteusMessageUnpackerImpl(
    private val selfUserId: UserId,
    private val protoContentMapper: ProtoContentMapper = MapperProvider.protoContentMapper(selfUserId = selfUserId),
    private val idMapper: IdMapper = MapperProvider.idMapper(),
) : ProteusMessageUnpacker {

    private val logger get() = kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER)

    override suspend fun <T : Any> unpackProteusMessage(
        proteusContext: ProteusCoreCryptoContext,
        event: Event.Conversation.NewMessage,
        handleMessage: suspend (applicationMessage: MessageUnpackResult.ApplicationMessage) -> T
    ): Either<CoreFailure, T> {
        val decodedContentBytes = Base64.decode(event.content)
        val cryptoSessionId = CryptoSessionId(
            idMapper.toCryptoQualifiedIDId(event.senderUserId),
            CryptoClientId(event.senderClientId.value)
        )
        return wrapProteusRequest {
            proteusContext.decryptMessage(cryptoSessionId, decodedContentBytes) {
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
            .flatMap { it }
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
                when (ProteusMessageFailureHandler.handleFailure(it)) {
                    ProteusMessageFailureResolution.Ignore -> {
                        logger.i("Ignoring duplicate ProteusFailure when processing message: $loggableException")
                    }

                    ProteusMessageFailureResolution.RecoverSession -> {
                        logger.w("ProteusFailure requires session recovery: $loggableException")
                    }

                    ProteusMessageFailureResolution.InformUser -> {
                        logger.e("ProteusFailure when processing message: $loggableException")
                    }
                }
            }

            else -> logger.e("Failure when processing message: $it")
        }
    }

    private fun getReadableMessageContent(
        plainMessageBlob: PlainMessageBlob,
        encryptedData: EncryptedData?,
    ): Either<CoreFailure, ProtoContent.Readable> = wrapProteusRequest {
        PlainMessageBlob(WireCallingMessageCodec.resolveExternal(plainMessageBlob.data, encryptedData?.data))
    }.map(protoContentMapper::decodeFromProtobuf).flatMap { decodedProtobuf ->
        if (decodedProtobuf !is ProtoContent.Readable) {
            Either.Left(CoreFailure.Unknown(IllegalArgumentException("Message content is not readable")))
        } else {
            Either.Right(decodedProtobuf)
        }
    }
}

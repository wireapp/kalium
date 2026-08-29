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

import com.wire.kalium.common.error.ProteusFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.CryptoUserID
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.cryptography.ProteusCoreCryptoContext
import com.wire.kalium.cryptography.utils.EncryptedData
import com.wire.kalium.cryptography.utils.PlainData
import com.wire.kalium.cryptography.utils.encryptDataWithAES256
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentDecoder
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.protobuf.encodeToByteArray
import com.wire.kalium.protobuf.messages.GenericMessage
import com.wire.kalium.protobuf.messages.Text
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail

private val TEST_CONVERSATION_ID = ConversationId("valueConvo", "domainConvo")
private val TEST_SENDER_USER_ID = UserId("41d2b365-f4a9-4ba1-bddf-5afb8aca6786", "domain")
private val TEST_SENDER_CLIENT_ID = ClientId("test")

private fun newMessageEvent(
    encryptedContent: String,
    senderUserId: UserId = TEST_SENDER_USER_ID,
    encryptedExternalContent: EncryptedData? = null
) = Event.Conversation.NewMessage(
    id = "eventId",
    conversationId = TEST_CONVERSATION_ID,
    senderUserId = senderUserId,
    senderClientId = TEST_SENDER_CLIENT_ID,
    messageInstant = Instant.UNIX_FIRST_DATE,
    content = encryptedContent,
    encryptedExternalContent = encryptedExternalContent,
)

private inline infix fun <L, R> Either<L, R>.shouldSucceed(crossinline successAssertion: (R) -> Unit) {
    when (this) {
        is Either.Left -> fail("Expected a Right value but got Left: $value")
        is Either.Right -> successAssertion(value)
    }
}

private inline infix fun <L, R> Either<L, R>.shouldFail(crossinline failureAssertion: (L) -> Unit) {
    when (this) {
        is Either.Left -> failureAssertion(value)
        is Either.Right -> fail("Expected a Left value but got Right: $value")
    }
}

class ProteusMessageUnpackerTest {

    @Test
    fun givenNewMessageEvent_whenUnpacking_shouldAskProteusClientForDecryption() = runTest {
        val (arrangement, proteusUnpacker) = Arrangement()
            .withProteusClientDecryptingByteArray(decryptedData = byteArrayOf())
            .withProtoContentDecoderReturning(
                { true },
                ProtoContent.Readable(
                    "uuid",
                    MessageContent.Unknown(),
                    false,
                    Conversation.LegalHoldStatus.DISABLED
                )
            ).arrange()

        val encodedEncryptedContent = Base64.encode("Hello".encodeToByteArray())
        val messageEvent = newMessageEvent(encodedEncryptedContent)
        proteusUnpacker.unpackProteusMessage(arrangement.proteusContext, messageEvent) { }

        val cryptoSessionId = CryptoSessionId(
            CryptoUserID(messageEvent.senderUserId.value, messageEvent.senderUserId.domain),
            CryptoClientId(messageEvent.senderClientId.value)
        )

        val decodedByteArray = Base64.decode(messageEvent.content)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.proteusContext.decryptMessage<Any>(eq(cryptoSessionId), matches { it.contentEquals(decodedByteArray) }, any())
        }
    }

    @Test
    fun givenNewMessageEventWithExternalContent_whenUnpacking_shouldReturnDecryptedExternalMessage() = runTest {
        val aesKey = generateRandomAES256Key()
        val messageUid = "uuid"
        val externalInstructions = ProtoContent.ExternalMessageInstructions(
            messageUid,
            aesKey.data,
            sha256 = null,
            encryptionAlgorithm = null
        )
        val plainTextContent = "Hello!"

        val protobufExternalContent = GenericMessage(
            content = GenericMessage.Content.Text(Text(plainTextContent)),
            messageId = messageUid
        )
        val encryptedProtobufExternalContent = encryptDataWithAES256(PlainData(protobufExternalContent.encodeToByteArray()), aesKey)
        val decryptedExternalContent = MessageContent.Text(plainTextContent)
        val emptyArray = byteArrayOf()

        val (arrangement, proteusUnpacker) = Arrangement()
            .withProteusClientDecryptingByteArray(decryptedData = emptyArray)
            .withProtoContentDecoderReturning(
                { it.data.contentEquals(emptyArray) },
                externalInstructions
            )
            .withProtoContentDecoderReturning(
                { it.data.contentEquals(protobufExternalContent.encodeToByteArray()) },
                ProtoContent.Readable(
                    messageUid,
                    decryptedExternalContent,
                    false,
                    Conversation.LegalHoldStatus.DISABLED
                )
            ).arrange()

        val messageEvent = newMessageEvent(
            Base64.encode("anything".encodeToByteArray()),
            encryptedExternalContent = encryptedProtobufExternalContent
        )

        val result = proteusUnpacker.unpackProteusMessage(arrangement.proteusContext, messageEvent) { it }

        result.shouldSucceed {
            assertIs<MessageUnpackResult.ApplicationMessage>(it)
            val content = it.content
            assertIs<ProtoContent.Readable>(content)
            assertEquals(decryptedExternalContent, content.messageContent)
        }
    }

    @Test
    fun givenProteusFailure_whenUnpacking_thenEveryFailureResolutionIsPropagated() = runTest {
        val codes = listOf(
            ProteusException.Code.DUPLICATE_MESSAGE,
            ProteusException.Code.SESSION_NOT_FOUND,
            ProteusException.Code.INVALID_SIGNATURE,
        )

        codes.forEach { code ->
            val exception = ProteusException("proteus failure", code, 1)
            val (arrangement, proteusUnpacker) = Arrangement()
                .withProteusClientThrowing(exception)
                .arrange()
            val event = newMessageEvent(Base64.encode("message".encodeToByteArray()))

            val result = proteusUnpacker.unpackProteusMessage(arrangement.proteusContext, event) { it }

            result.shouldFail {
                assertIs<ProteusFailure>(it)
                assertEquals(code, it.proteusException.code)
            }
        }
    }

    private class Arrangement {
        val proteusContext = mock<ProteusCoreCryptoContext>(mode = MockMode.autoUnit)
        val protoContentDecoder = mock<ProtoContentDecoder>()

        suspend fun withProteusClientDecryptingByteArray(decryptedData: ByteArray) = apply {
            everySuspend {
                proteusContext.decryptMessage<Either<*, *>>(any(), any(), any())
            } calls {
                val lambda = it.args[2] as suspend (ByteArray) -> Either<*, *>
                lambda.invoke(decryptedData)
            }
        }

        suspend fun withProteusClientThrowing(exception: ProteusException) = apply {
            everySuspend {
                proteusContext.decryptMessage<Either<*, *>>(any(), any(), any())
            } calls {
                throw exception
            }
        }

        fun withProtoContentDecoderReturning(plainBlobMatcher: (PlainMessageBlob) -> Boolean, protoContent: ProtoContent) = apply {
            every {
                protoContentDecoder.decodeFromProtobuf(matches { plainBlobMatcher(it) })
            } returns protoContent
        }

        fun arrange(block: suspend Arrangement.() -> Unit = {}) = let {
            runBlocking { block() }
            this to ProteusMessageUnpackerImpl(protoContentDecoder)
        }

    }

}

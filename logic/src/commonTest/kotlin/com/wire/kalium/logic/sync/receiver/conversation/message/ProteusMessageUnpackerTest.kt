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
import com.wire.kalium.cryptography.CryptoUserID
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.utils.PlainData
import com.wire.kalium.cryptography.utils.encryptDataWithAES256
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.logic.data.client.ProteusClientProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.util.Base64
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.protobuf.encodeToByteArray
import com.wire.kalium.protobuf.messages.GenericMessage
import com.wire.kalium.protobuf.messages.Text
import io.ktor.utils.io.core.toByteArray
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.fake.valueOf
import io.mockative.matchers.AnyMatcher
import io.mockative.matchers.Matcher
import io.mockative.matchers.PredicateMatcher
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProteusMessageUnpackerTest {

    @Test
    fun givenNewMessageEvent_whenUnpacking_shouldAskProteusClientForDecryption() = runTest {
        val (arrangement, proteusUnpacker) = Arrangement()
            .withProteusClientDecryptingByteArray(decryptedData = byteArrayOf())
            .withProtoContentMapperReturning(
                AnyMatcher(valueOf()),
                ProtoContent.Readable(
                    "uuid",
                    MessageContent.Unknown(),
                    false,
                    Conversation.LegalHoldStatus.DISABLED
                )
            ).arrange()

        val encodedEncryptedContent = Base64.encodeToBase64("Hello".encodeToByteArray())
        val messageEvent = TestEvent.newMessageEvent(encodedEncryptedContent.decodeToString())
        proteusUnpacker.unpackProteusMessage(messageEvent) {  }

        val cryptoSessionId = CryptoSessionId(
            CryptoUserID(messageEvent.senderUserId.value, messageEvent.senderUserId.domain),
            CryptoClientId(messageEvent.senderClientId.value)
        )

        val decodedByteArray = Base64.decodeFromBase64(messageEvent.content.toByteArray())
        coVerify {
            arrangement.proteusClient.decrypt<Any>(matches { it.contentEquals(decodedByteArray) }, eq(cryptoSessionId), any())
        }.wasInvoked(exactly = once)
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

        val (_, proteusUnpacker) = Arrangement()
            .withProteusClientDecryptingByteArray(decryptedData = emptyArray)
            .withProtoContentMapperReturning(
                PredicateMatcher(PlainMessageBlob::class, valueOf()) { it.data.contentEquals(emptyArray) },
                externalInstructions
            )
            .withProtoContentMapperReturning(
                PredicateMatcher(PlainMessageBlob::class, valueOf()) { it.data.contentEquals(protobufExternalContent.encodeToByteArray()) },
                ProtoContent.Readable(
                    messageUid,
                    decryptedExternalContent,
                    false,
                    Conversation.LegalHoldStatus.DISABLED
                )
            ).arrange()

        val messageEvent = TestEvent.newMessageEvent(
            Base64.encodeToBase64("anything".encodeToByteArray()).decodeToString(),
            encryptedExternalContent = encryptedProtobufExternalContent
        )

        val result = proteusUnpacker.unpackProteusMessage(messageEvent) { it }

        result.shouldSucceed {
            assertIs<MessageUnpackResult.ApplicationMessage>(it)
            val content = it.content
            assertIs<ProtoContent.Readable>(content)
            assertEquals(decryptedExternalContent, content.messageContent)
        }
    }

    private class Arrangement {
        @Mock
        val proteusClient = mock(ProteusClient::class)

        @Mock
        val proteusClientProvider = mock(ProteusClientProvider::class)

        @Mock
        val protoContentMapper = mock(ProtoContentMapper::class)

        suspend fun withProteusClientDecryptingByteArray(decryptedData: ByteArray) = apply {
            coEvery {
                proteusClient.decrypt<Either<*, *>>(any(), any(), any())
            }.invokes { args ->
                val lambda = args[2] as suspend (ByteArray) -> Either<*, *>
                lambda.invoke(decryptedData)
            }
        }

        fun withProtoContentMapperReturning(plainBlobMatcher: Matcher<PlainMessageBlob>, protoContent: ProtoContent) = apply {
            every {
                protoContentMapper.decodeFromProtobuf(matches { plainBlobMatcher.matches(it) })
            }.returns(protoContent)
        }

        suspend fun arrange() = run {
            coEvery {
                proteusClientProvider.getOrError()
            }.returns(Either.Right(proteusClient))
            this to ProteusMessageUnpackerImpl(
                proteusClientProvider, SELF_USER_ID, protoContentMapper
            )
        }

        companion object {
            val SELF_USER_ID = UserId("user-id", "domain")
        }
    }

}

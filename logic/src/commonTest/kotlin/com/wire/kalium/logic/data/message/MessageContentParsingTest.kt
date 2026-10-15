/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.data.message

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.protobuf.encodeToByteArray
import com.wire.kalium.protobuf.messages.GenericMessage
import com.wire.kalium.protobuf.messages.HistoryClient
import com.wire.kalium.protobuf.messages.HistoryClientAvailable
import dev.mokkery.answering.calls
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import pbandk.ByteArr
import pbandk.InvalidProtocolBufferException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class MessageContentParsingTest {
    private val mapper = ProtoContentMapperImpl(selfUserId = UserId("self", "example.com"))

    @Test
    fun givenUnterminatedVarint_whenDecoding_thenReturnInvalidMessage() {
        val blob = PlainMessageBlob(byteArrayOf(0x80.toByte()))
        assertFailsWith<InvalidProtocolBufferException> { mapper.decodeFromProtobuf(blob) }
        assertEquals(Either.Left(InvalidMessageContent), mapper.decodeFromProtobufSafely(blob))
    }

    @Test
    fun givenInvalidHistoryTimestamp_whenDecoding_thenReturnInvalidMessage() {
        val blob = PlainMessageBlob(GenericMessage(
            messageId = "invalid-history",
            content = GenericMessage.Content.HistoryClientAvailable(HistoryClientAvailable(
                HistoryClient(clientId = "client", secret = ByteArr(byteArrayOf(1, 2, 3)), createdAt = "not-an-instant")
            )),
        ).encodeToByteArray())
        assertFailsWith<IllegalArgumentException> { mapper.decodeFromProtobuf(blob) }
        assertEquals(Either.Left(InvalidMessageContent), mapper.decodeFromProtobufSafely(blob))
    }

    @Test
    fun givenMalformedCallingJson_whenDecoding_thenReturnInvalidMessage() {
        val content = MessageContent.Calling(value = "{", conversationId = null)
        assertFailsWith<SerializationException> { Json.decodeFromString<MessageContent.Calling.CallingValue>(content.value) }
        assertEquals(Either.Left(InvalidMessageContent), content.decodeCallingValue(Json))
    }

    @Test
    fun givenCancellationOrRuntimeFailure_whenDecoding_thenRethrowIt() {
        val mapper = mock<ProtoContentMapper>()
        for (failure in listOf(CancellationException("cancelled"), IllegalStateException("bug"), AssertionError("fatal"))) {
            every { mapper.decodeFromProtobuf(any()) } calls { throw failure }
            val thrown = assertFailsWith<Throwable> { mapper.decodeFromProtobufSafely(PlainMessageBlob(byteArrayOf())) }
            assertSame(failure, thrown)
        }
    }
}

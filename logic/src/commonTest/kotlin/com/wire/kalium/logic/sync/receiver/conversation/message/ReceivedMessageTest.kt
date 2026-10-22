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

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.RegularMessageMapper
import com.wire.kalium.logic.data.message.withGroupReceiptMode
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.MessageContentEncoder
import com.wire.kalium.persistence.dao.message.MessageDAO
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ReceivedMessageTest {
    private val selfUserId = TestUser.SELF.id
    private val messageDAO = mock<MessageDAO>()
    private val mapper = RegularMessageMapper(selfUserId)
    private val encoder = MessageContentEncoder()

    @Test
    fun quoteStorageFailureIsDistinctFromMissingOriginalAndCancellationPropagates() = runTest {
        val message = TestMessage.TEXT_MESSAGE.copy(content = MessageContent.Text(
            "reply", quotedMessageReference = MessageContent.QuoteReference("missing", byteArrayOf(1), true)
        ))
        everySuspend { messageDAO.getMessageById(any(), any()) } throws IllegalStateException("Protected storage")
        assertIs<Either.Left<*>>(prepareReceivedTextMessage(message, messageDAO, selfUserId))
        everySuspend { messageDAO.getMessageById(any(), any()) } returns null
        val prepared = assertIs<Either.Right<Message.Regular>>(prepareReceivedTextMessage(message, messageDAO, selfUserId)).value
        assertFalse(checkNotNull((prepared.content as MessageContent.Text).quotedMessageReference).isVerified)
        everySuspend { messageDAO.getMessageById(any(), any()) } throws CancellationException("Attempt expired")
        assertFailsWith<CancellationException> { prepareReceivedTextMessage(message, messageDAO, selfUserId) }
    }

    @Test
    fun textQuotesVerifyAgainstStoredTextAssetLocationMultipartAndPollContent() = runTest {
        for (content in listOf(
            TestMessage.TEXT_CONTENT,
            TestMessage.ASSET_CONTENT,
            MessageContent.Location(1f, 2f, "location", 10),
            MessageContent.Multipart(value = "multipart"),
            MessageContent.Poll("poll", listOf(MessageContent.Poll.Option("a", "Yes"), MessageContent.Poll.Option("b", "No")), false),
        )) {
            val original = TestMessage.TEXT_MESSAGE.copy(content = content)
            everySuspend { messageDAO.getMessageById(any(), any()) } returns mapper.toEntity(original)
            val hash = checkNotNull(encoder.encodeMessageContent(original.date, original.content)).sha256Digest
            val message = original.copy(id = "reply", content = MessageContent.Text(
                "reply", quotedMessageReference = MessageContent.QuoteReference(original.id, hash, false)
            ))
            val prepared = assertIs<Either.Right<Message.Regular>>(prepareReceivedTextMessage(message, messageDAO, selfUserId)).value
            assertTrue(checkNotNull((prepared.content as MessageContent.Text).quotedMessageReference).isVerified)
        }
    }

    @Test
    fun receivedRegularMessagePreservesServerTimestampAndAppTimerOrigins() {
        val source = TestMessage.TEXT_MESSAGE
        for (sender in listOf(selfUserId, TestUser.OTHER_USER_ID)) {
            val unpacked = MessageUnpackResult.ApplicationMessage(
                source.conversationId, source.date, sender, source.senderClientId,
                ProtoContent.Readable("received", TestMessage.TEXT_CONTENT, true, Conversation.LegalHoldStatus.DISABLED, 10_000)
            )
            val receivedAt = source.date + 5.seconds
            val message = unpacked.toRegularMessage(selfUserId, receivedAt)
            assertEquals(source.date, message.date)
            assertEquals(sender == selfUserId, message.isSelfMessage)
            val expectedStatus = if (sender == selfUserId) Message.ExpirationData.SelfDeletionStatus.Started(receivedAt + 10.seconds)
                else Message.ExpirationData.SelfDeletionStatus.NotStarted
            assertEquals(Message.ExpirationData(10.seconds, expectedStatus), message.expirationData)
            assertEquals(message.expirationData, mapper.fromEntity(mapper.toEntity(message)).expirationData)
            assertTrue(message.expectsReadConfirmation)
            assertFalse(message.withGroupReceiptMode(null).expectsReadConfirmation)
            assertTrue(message.withGroupReceiptMode(Conversation.ReceiptMode.ENABLED).expectsReadConfirmation)
        }
    }
}

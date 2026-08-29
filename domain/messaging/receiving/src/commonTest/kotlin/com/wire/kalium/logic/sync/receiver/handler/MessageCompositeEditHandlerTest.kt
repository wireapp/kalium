/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.MessageId
import com.wire.kalium.logic.data.message.CompositeEditMessageMetadataRepository
import com.wire.kalium.logic.data.message.CompositeMessageRepository
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.composite.Button
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class MessageCompositeEditHandlerTest {

    @Test
    fun givenMatchingOriginalSender_whenHandling_thenExactLookupAndUpdateArgumentsAreUsedOnce() = runTest {
        val updateResult: Either<StorageFailure, Unit> = Either.Right(Unit)
        val (metadataRepository, compositeRepository, handler) = arrangement(
            lookupResult = Either.Right(originalSenderId),
            updateResult = updateResult,
        )

        val result = handler.handle(signalingMessage, compositeEdited)

        assertSame(updateResult, result)
        assertEquals(listOf(LookupCall(envelopeConversationId, originalMessageId)), metadataRepository.calls)
        assertEquals(1, compositeRepository.calls.size)
        with(compositeRepository.calls.single()) {
            assertEquals(envelopeConversationId, conversationId)
            assertSame(compositeEdited, messageContent)
            assertEquals(signalingMessageId, newMessageId)
            assertEquals(signalingDate, editInstant)
        }
    }

    @Test
    fun givenMissingOriginalMessage_whenHandling_thenDataNotFoundIsReturnedAndUpdateIsSkipped() = runTest {
        val (metadataRepository, compositeRepository, handler) = arrangement(
            lookupResult = Either.Left(StorageFailure.DataNotFound),
        )

        val result = handler.handle(signalingMessage, compositeEdited)

        assertEquals(Either.Left(StorageFailure.DataNotFound), result)
        assertEquals(listOf(LookupCall(envelopeConversationId, originalMessageId)), metadataRepository.calls)
        assertEquals(emptyList(), compositeRepository.calls)
    }

    @Test
    fun givenSenderLookupFailure_whenHandling_thenExactFailureIsReturnedAndUpdateIsSkipped() = runTest {
        val expectedFailure = StorageFailure.Generic(IllegalStateException("sender lookup failed"))
        val (_, compositeRepository, handler) = arrangement(
            lookupResult = Either.Left(expectedFailure),
        )

        val result = handler.handle(signalingMessage, compositeEdited)

        assertSame(expectedFailure, assertIs<Either.Left<CoreFailure>>(result).value)
        assertEquals(emptyList(), compositeRepository.calls)
    }

    @Test
    fun givenEditSenderDoesNotMatchOriginalSender_whenHandling_thenDataNotFoundIsReturnedAndUpdateIsSkipped() = runTest {
        val (metadataRepository, compositeRepository, handler) = arrangement(
            lookupResult = Either.Right(otherUserId),
        )

        val result = handler.handle(signalingMessage, compositeEdited)

        assertEquals(Either.Left(StorageFailure.DataNotFound), result)
        assertEquals(listOf(LookupCall(envelopeConversationId, originalMessageId)), metadataRepository.calls)
        assertEquals(emptyList(), compositeRepository.calls)
    }

    @Test
    fun givenCompositeUpdateFailure_whenHandling_thenExactUpdateResultIsReturned() = runTest {
        val updateFailure = StorageFailure.Generic(IllegalStateException("composite update failed"))
        val updateResult: Either<StorageFailure, Unit> = Either.Left(updateFailure)
        val (_, compositeRepository, handler) = arrangement(
            lookupResult = Either.Right(originalSenderId),
            updateResult = updateResult,
        )

        val result = handler.handle(signalingMessage, compositeEdited)

        assertSame(updateResult, result)
        assertEquals(1, compositeRepository.calls.size)
    }

    private fun arrangement(
        lookupResult: Either<StorageFailure, UserId>,
        updateResult: Either<StorageFailure, Unit> = Either.Right(Unit),
    ): Triple<RecordingMetadataRepository, RecordingCompositeRepository, MessageCompositeEditHandler> {
        val metadataRepository = RecordingMetadataRepository(lookupResult)
        val compositeRepository = RecordingCompositeRepository(updateResult)
        return Triple(
            metadataRepository,
            compositeRepository,
            MessageCompositeEditHandlerImpl(metadataRepository, compositeRepository),
        )
    }

    private class RecordingMetadataRepository(
        private val result: Either<StorageFailure, UserId>,
    ) : CompositeEditMessageMetadataRepository {
        val calls = mutableListOf<LookupCall>()

        override suspend fun originalSenderIdForCompositeEdit(
            conversationId: ConversationId,
            messageId: MessageId,
        ): Either<StorageFailure, UserId> {
            calls += LookupCall(conversationId, messageId)
            return result
        }
    }

    private class RecordingCompositeRepository(
        private val result: Either<StorageFailure, Unit>,
    ) : CompositeMessageRepository {
        val calls = mutableListOf<UpdateCall>()

        override suspend fun markSelected(
            messageId: MessageId,
            conversationId: ConversationId,
            buttonId: String,
        ): Either<StorageFailure, Unit> = Either.Right(Unit)

        override suspend fun resetSelection(
            messageId: MessageId,
            conversationId: ConversationId,
        ): Either<StorageFailure, Unit> = Either.Right(Unit)

        override suspend fun updateCompositeMessage(
            conversationId: ConversationId,
            messageContent: MessageContent.CompositeEdited,
            newMessageId: MessageId,
            editInstant: Instant,
        ): Either<StorageFailure, Unit> {
            calls += UpdateCall(conversationId, messageContent, newMessageId, editInstant)
            return result
        }
    }

    private data class LookupCall(
        val conversationId: ConversationId,
        val messageId: MessageId,
    )

    private data class UpdateCall(
        val conversationId: ConversationId,
        val messageContent: MessageContent.CompositeEdited,
        val newMessageId: MessageId,
        val editInstant: Instant,
    )

    private companion object {
        const val originalMessageId = "original-message-id"
        const val signalingMessageId = "signaling-message-id"
        val envelopeConversationId = ConversationId("envelope-conversation", "wire.example")
        val originalSenderId = UserId("original-sender", "wire.example")
        val otherUserId = UserId("other-user", "wire.example")
        val signalingDate = Instant.parse("2026-08-19T10:15:30Z")
        val compositeEdited = MessageContent.CompositeEdited(
            editMessageId = originalMessageId,
            newTextContent = MessageContent.Text("edited body"),
            newButtonList = listOf(
                Button(text = "first", id = "first-id", isSelected = false),
                Button(text = "second", id = "second-id", isSelected = true),
            ),
        )
        val signalingMessage = Message.Signaling(
            id = signalingMessageId,
            content = compositeEdited,
            conversationId = envelopeConversationId,
            date = signalingDate,
            senderUserId = originalSenderId,
            senderClientId = ClientId("sender-client"),
            status = Message.Status.Sent,
            isSelfMessage = false,
            expirationData = null,
        )
    }
}

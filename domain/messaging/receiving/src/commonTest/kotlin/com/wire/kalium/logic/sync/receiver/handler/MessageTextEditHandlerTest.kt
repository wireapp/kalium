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

package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEditState
import com.wire.kalium.logic.data.message.linkpreview.MessageLinkPreview
import com.wire.kalium.logic.data.message.mention.MessageMention
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MessageTextEditHandlerTest {

    @Test
    fun givenLookupFailure_whenHandling_thenExactFailureIsReturnedWithoutSideEffects() = runTest {
        val failure = storageFailure("lookup")
        val arrangement = arrangement(Either.Left(failure))

        val result = arrangement.handler.handle(textMessage, textEdit)

        assertSame(failure, (result as Either.Left).value)
        assertEquals(listOf(RecordingMessageEditPersistence.LOAD), arrangement.events)
        assertTrue(arrangement.persistence.textCalls.isEmpty())
        assertTrue(arrangement.notifications.textCalls.isEmpty())
    }

    @Test
    fun givenSenderMismatch_whenHandling_thenDataNotFoundIsReturnedWithoutSideEffects() = runTest {
        val arrangement = arrangement(Either.Right(textState(sender = otherSenderId)))

        val result = arrangement.handler.handle(textMessage, textEdit)

        assertEquals(Either.Left(StorageFailure.DataNotFound), result)
        assertEquals(listOf(RecordingMessageEditPersistence.LOAD), arrangement.events)
        assertTrue(arrangement.persistence.textCalls.isEmpty())
        assertTrue(arrangement.notifications.textCalls.isEmpty())
    }

    @Test
    fun givenMatchingTextIsNotEdited_whenHandling_thenNotificationPrecedesIncomingUpdateWithoutStatus() = runTest {
        val updateResult: Either<CoreFailure, Unit> = Either.Right(Unit)
        val arrangement = arrangement(Either.Right(textState(lastEditInstant = null)))
        arrangement.persistence.textResult = updateResult

        val result = arrangement.handler.handle(textMessage, textEdit)

        assertSame(updateResult, result)
        assertIncomingUpdate(arrangement)
        assertEquals(listOf("load", "notify", "update"), arrangement.events)
        assertTrue(arrangement.persistence.statusCalls.isEmpty())
    }

    @Test
    fun givenStoredContentIsNotText_whenHandling_thenNotificationPrecedesIncomingUpdateWithoutStatus() = runTest {
        val arrangement = arrangement(Either.Right(otherContentState))

        arrangement.handler.handle(textMessage, textEdit)

        assertIncomingUpdate(arrangement)
        assertEquals(listOf("load", "notify", "update"), arrangement.events)
        assertTrue(arrangement.persistence.statusCalls.isEmpty())
    }

    @Test
    fun givenLocalEditIsNewer_whenHandling_thenLocalFieldsWinAndIncomingLinkPreviewsAreRetained() = runTest {
        val localInstant = Instant.parse("2026-08-19T10:15:31Z")
        val localMentions = listOf(localMention)
        val updateFailure = storageFailure("local update")
        val updateResult: Either<CoreFailure, Unit> = Either.Left(updateFailure)
        val arrangement = arrangement(Either.Right(textState("local body", localMentions, localInstant)))
        arrangement.persistence.textResult = updateResult

        val result = arrangement.handler.handle(textMessage, textEdit)

        assertSame(updateResult, result)
        val call = arrangement.persistence.textCalls.single()
        assertEquals(
            textEdit.copy(newContent = "local body", newMentions = localMentions),
            call.content,
        )
        assertEquals(incomingMessageId, call.newMessageId)
        assertEquals(localInstant, call.editInstant)
        assertEquals(listOf("load", "update"), arrangement.events)
        assertTrue(arrangement.notifications.textCalls.isEmpty())
        assertTrue(arrangement.persistence.statusCalls.isEmpty())
    }

    @Test
    fun givenIncomingEditIsNewer_whenHandling_thenNotificationUpdateAndStatusRunInOrder() = runTest {
        val arrangement = arrangement(Either.Right(textState(lastEditInstant = olderEditInstant)))

        arrangement.handler.handle(textMessage, textEdit)

        assertIncomingUpdate(arrangement)
        assertEquals(listOf("load", "notify", "update", "status"), arrangement.events)
        assertEquals(listOf(envelopeConversationId to incomingMessageId), arrangement.persistence.statusCalls)
    }

    @Test
    fun givenEditTimestampsAreEqual_whenHandling_thenIncomingEditPathIsUsed() = runTest {
        val arrangement = arrangement(Either.Right(textState(lastEditInstant = incomingEditInstant)))

        arrangement.handler.handle(textMessage, textEdit)

        assertIncomingUpdate(arrangement)
        assertEquals(listOf("load", "notify", "update", "status"), arrangement.events)
    }

    @Test
    fun givenIncomingUpdateFails_whenHandling_thenExactFailureIsReturnedAndStatusIsSkipped() = runTest {
        val failure = storageFailure("update")
        val updateResult: Either<CoreFailure, Unit> = Either.Left(failure)
        val arrangement = arrangement(Either.Right(textState(lastEditInstant = olderEditInstant)))
        arrangement.persistence.textResult = updateResult

        val result = arrangement.handler.handle(textMessage, textEdit)

        assertSame(failure, (result as Either.Left).value)
        assertEquals(listOf("load", "notify", "update"), arrangement.events)
        assertTrue(arrangement.persistence.statusCalls.isEmpty())
    }

    @Test
    fun givenStatusUpdateFails_whenHandling_thenExactStatusFailureIsReturned() = runTest {
        val failure = storageFailure("status")
        val statusResult: Either<CoreFailure, Unit> = Either.Left(failure)
        val arrangement = arrangement(Either.Right(textState(lastEditInstant = olderEditInstant)))
        arrangement.persistence.statusResult = statusResult

        val result = arrangement.handler.handle(textMessage, textEdit)

        assertSame(failure, (result as Either.Left).value)
        assertEquals(listOf("load", "notify", "update", "status"), arrangement.events)
    }

    @Test
    fun givenNotificationThrows_whenHandling_thenExceptionEscapesBeforeUpdate() = runTest {
        val expected = IllegalStateException("notification")
        val arrangement = arrangement(Either.Right(textState(lastEditInstant = olderEditInstant)))
        arrangement.notifications.throwable = expected

        val actual = assertFailsWith<IllegalStateException> {
            arrangement.handler.handle(textMessage, textEdit)
        }

        assertSame(expected, actual)
        assertEquals(listOf("load", "notify"), arrangement.events)
        assertTrue(arrangement.persistence.textCalls.isEmpty())
    }

    @Test
    fun givenPersistenceThrows_whenHandling_thenExceptionEscapesAfterNotification() = runTest {
        val expected = IllegalStateException("persistence")
        val arrangement = arrangement(Either.Right(textState(lastEditInstant = olderEditInstant)))
        arrangement.persistence.throwableOperation = RecordingMessageEditPersistence.UPDATE
        arrangement.persistence.throwable = expected

        val actual = assertFailsWith<IllegalStateException> {
            arrangement.handler.handle(textMessage, textEdit)
        }

        assertSame(expected, actual)
        assertEquals(listOf("load", "notify", "update"), arrangement.events)
        assertTrue(arrangement.persistence.statusCalls.isEmpty())
    }

    @Test
    fun givenPersistenceCancellation_whenHandling_thenCancellationEscapesUnchanged() = runTest {
        val expected = CancellationException("cancelled")
        val arrangement = arrangement(Either.Right(textState(lastEditInstant = olderEditInstant)))
        arrangement.persistence.throwableOperation = RecordingMessageEditPersistence.UPDATE
        arrangement.persistence.throwable = expected

        val actual = assertFailsWith<CancellationException> {
            arrangement.handler.handle(textMessage, textEdit)
        }

        assertSame(expected, actual)
        assertEquals(listOf("load", "notify", "update"), arrangement.events)
        assertTrue(arrangement.persistence.statusCalls.isEmpty())
    }

    private fun assertIncomingUpdate(arrangement: Arrangement) {
        val notificationCall = arrangement.notifications.textCalls.single()
        assertSame(textMessage, notificationCall.first)
        assertSame(textEdit, notificationCall.second)
        val call = arrangement.persistence.textCalls.single()
        assertEquals(envelopeConversationId, call.conversationId)
        assertSame(textEdit, call.content)
        assertEquals(incomingMessageId, call.newMessageId)
        assertEquals(incomingEditInstant, call.editInstant)
    }

    private fun arrangement(loadResult: Either<StorageFailure, MessageEditState>): Arrangement {
        val events = mutableListOf<String>()
        val persistence = RecordingMessageEditPersistence(loadResult, events)
        val notifications = RecordingEditNotificationManager(events)
        return Arrangement(
            persistence,
            notifications,
            events,
            MessageTextEditHandlerImpl(persistence, notifications),
        )
    }

    private data class Arrangement(
        val persistence: RecordingMessageEditPersistence,
        val notifications: RecordingEditNotificationManager,
        val events: MutableList<String>,
        val handler: MessageTextEditHandler,
    )

    private companion object {
        val olderEditInstant = Instant.parse("2026-08-19T10:15:29Z")
        val localMention = MessageMention(0, 5, originalSenderId, isSelfMention = false)
        val incomingMention = MessageMention(7, 6, otherSenderId, isSelfMention = false)
        val incomingPreview = MessageLinkPreview("https://wire.example", 0, title = "Wire")
        val textEdit = MessageContent.TextEdited(
            editMessageId = originalMessageId,
            newContent = "incoming body",
            newLinkPreviews = listOf(incomingPreview),
            newMentions = listOf(incomingMention),
        )
        val textMessage = signalingMessage(textEdit)

        fun textState(
            value: String = "stored body",
            mentions: List<MessageMention> = listOf(localMention),
            lastEditInstant: Instant? = olderEditInstant,
            sender: com.wire.kalium.logic.data.user.UserId = originalSenderId,
        ) = MessageEditState(
            senderUserId = sender,
            content = MessageEditState.Content.Text(value, mentions, lastEditInstant),
        )
    }
}

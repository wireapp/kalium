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
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.message.CellAssetContent
import com.wire.kalium.logic.data.message.MessageAttachment
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEditState
import com.wire.kalium.logic.data.message.mention.MessageMention
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MessageMultipartEditHandlerTest {

    @Test
    fun givenLookupFailure_whenHandling_thenExactFailureIsReturnedWithoutSideEffects() = runTest {
        val failure = storageFailure("lookup")
        val arrangement = arrangement(Either.Left(failure))

        val result = arrangement.handler.handle(multipartMessage, multipartEdit)

        assertSame(failure, (result as Either.Left).value)
        assertEquals(listOf(RecordingMessageEditPersistence.LOAD), arrangement.events)
        assertTrue(arrangement.persistence.multipartCalls.isEmpty())
        assertTrue(arrangement.notifications.multipartCalls.isEmpty())
    }

    @Test
    fun givenSenderMismatch_whenHandling_thenDataNotFoundIsReturnedWithoutSideEffects() = runTest {
        val arrangement = arrangement(Either.Right(multipartState(sender = otherSenderId)))

        val result = arrangement.handler.handle(multipartMessage, multipartEdit)

        assertEquals(Either.Left(StorageFailure.DataNotFound), result)
        assertEquals(listOf(RecordingMessageEditPersistence.LOAD), arrangement.events)
        assertTrue(arrangement.persistence.multipartCalls.isEmpty())
        assertTrue(arrangement.notifications.multipartCalls.isEmpty())
    }

    @Test
    fun givenMatchingMultipartIsNotEdited_whenHandling_thenNotificationPrecedesIncomingUpdateWithoutStatus() = runTest {
        val updateResult: Either<CoreFailure, Unit> = Either.Right(Unit)
        val arrangement = arrangement(Either.Right(multipartState(lastEditInstant = null)))
        arrangement.persistence.multipartResult = updateResult

        val result = arrangement.handler.handle(multipartMessage, multipartEdit)

        assertSame(updateResult, result)
        assertIncomingUpdate(arrangement)
        assertEquals(listOf("load", "notify", "update"), arrangement.events)
        assertTrue(arrangement.persistence.statusCalls.isEmpty())
    }

    @Test
    fun givenStoredContentIsNotMultipart_whenHandling_thenNotificationPrecedesIncomingUpdateWithoutStatus() = runTest {
        val arrangement = arrangement(Either.Right(otherContentState))

        arrangement.handler.handle(multipartMessage, multipartEdit)

        assertIncomingUpdate(arrangement)
        assertEquals(listOf("load", "notify", "update"), arrangement.events)
        assertTrue(arrangement.persistence.statusCalls.isEmpty())
    }

    @Test
    fun givenLocalEditIsNewer_whenHandling_thenLocalValueMentionsAndAttachmentsWin() = runTest {
        val localInstant = Instant.parse("2026-08-19T10:15:31Z")
        val localMentions = listOf(localMention)
        val localAttachments = listOf(localAttachment)
        val updateFailure = storageFailure("local update")
        val updateResult: Either<CoreFailure, Unit> = Either.Left(updateFailure)
        val arrangement = arrangement(
            Either.Right(multipartState("local body", localMentions, localAttachments, localInstant))
        )
        arrangement.persistence.multipartResult = updateResult

        val result = arrangement.handler.handle(multipartMessage, multipartEdit)

        assertSame(updateResult, result)
        val call = arrangement.persistence.multipartCalls.single()
        assertEquals(
            multipartEdit.copy(
                newTextContent = "local body",
                newMentions = localMentions,
                newAttachments = localAttachments,
            ),
            call.content,
        )
        assertEquals(incomingMessageId, call.newMessageId)
        assertEquals(localInstant, call.editInstant)
        assertEquals(listOf("load", "update"), arrangement.events)
        assertTrue(arrangement.notifications.multipartCalls.isEmpty())
        assertTrue(arrangement.persistence.statusCalls.isEmpty())
    }

    @Test
    fun givenIncomingEditIsNewer_whenHandling_thenNotificationUpdateAndStatusRunInOrder() = runTest {
        val arrangement = arrangement(Either.Right(multipartState(lastEditInstant = olderEditInstant)))

        arrangement.handler.handle(multipartMessage, multipartEdit)

        assertIncomingUpdate(arrangement)
        assertEquals(listOf("load", "notify", "update", "status"), arrangement.events)
        assertEquals(listOf(envelopeConversationId to incomingMessageId), arrangement.persistence.statusCalls)
    }

    @Test
    fun givenEditTimestampsAreEqual_whenHandling_thenIncomingEditPathIsUsed() = runTest {
        val arrangement = arrangement(Either.Right(multipartState(lastEditInstant = incomingEditInstant)))

        arrangement.handler.handle(multipartMessage, multipartEdit)

        assertIncomingUpdate(arrangement)
        assertEquals(listOf("load", "notify", "update", "status"), arrangement.events)
    }

    @Test
    fun givenIncomingUpdateFails_whenHandling_thenExactFailureIsReturnedAndStatusIsSkipped() = runTest {
        val failure = storageFailure("update")
        val updateResult: Either<CoreFailure, Unit> = Either.Left(failure)
        val arrangement = arrangement(Either.Right(multipartState(lastEditInstant = olderEditInstant)))
        arrangement.persistence.multipartResult = updateResult

        val result = arrangement.handler.handle(multipartMessage, multipartEdit)

        assertSame(failure, (result as Either.Left).value)
        assertEquals(listOf("load", "notify", "update"), arrangement.events)
        assertTrue(arrangement.persistence.statusCalls.isEmpty())
    }

    @Test
    fun givenStatusUpdateFails_whenHandling_thenExactStatusFailureIsReturned() = runTest {
        val failure = storageFailure("status")
        val statusResult: Either<CoreFailure, Unit> = Either.Left(failure)
        val arrangement = arrangement(Either.Right(multipartState(lastEditInstant = olderEditInstant)))
        arrangement.persistence.statusResult = statusResult

        val result = arrangement.handler.handle(multipartMessage, multipartEdit)

        assertSame(failure, (result as Either.Left).value)
        assertEquals(listOf("load", "notify", "update", "status"), arrangement.events)
    }

    @Test
    fun givenNotificationThrows_whenHandling_thenExceptionEscapesBeforeUpdate() = runTest {
        val expected = IllegalStateException("notification")
        val arrangement = arrangement(Either.Right(multipartState(lastEditInstant = olderEditInstant)))
        arrangement.notifications.throwable = expected

        val actual = assertFailsWith<IllegalStateException> {
            arrangement.handler.handle(multipartMessage, multipartEdit)
        }

        assertSame(expected, actual)
        assertEquals(listOf("load", "notify"), arrangement.events)
        assertTrue(arrangement.persistence.multipartCalls.isEmpty())
    }

    @Test
    fun givenPersistenceThrows_whenHandling_thenExceptionEscapesAfterNotification() = runTest {
        val expected = IllegalStateException("persistence")
        val arrangement = arrangement(Either.Right(multipartState(lastEditInstant = olderEditInstant)))
        arrangement.persistence.throwableOperation = RecordingMessageEditPersistence.UPDATE
        arrangement.persistence.throwable = expected

        val actual = assertFailsWith<IllegalStateException> {
            arrangement.handler.handle(multipartMessage, multipartEdit)
        }

        assertSame(expected, actual)
        assertEquals(listOf("load", "notify", "update"), arrangement.events)
        assertTrue(arrangement.persistence.statusCalls.isEmpty())
    }

    @Test
    fun givenPersistenceCancellation_whenHandling_thenCancellationEscapesUnchanged() = runTest {
        val expected = CancellationException("cancelled")
        val arrangement = arrangement(Either.Right(multipartState(lastEditInstant = olderEditInstant)))
        arrangement.persistence.throwableOperation = RecordingMessageEditPersistence.UPDATE
        arrangement.persistence.throwable = expected

        val actual = assertFailsWith<CancellationException> {
            arrangement.handler.handle(multipartMessage, multipartEdit)
        }

        assertSame(expected, actual)
        assertEquals(listOf("load", "notify", "update"), arrangement.events)
        assertTrue(arrangement.persistence.statusCalls.isEmpty())
    }

    private fun assertIncomingUpdate(arrangement: Arrangement) {
        val notificationCall = arrangement.notifications.multipartCalls.single()
        assertSame(multipartMessage, notificationCall.first)
        assertSame(multipartEdit, notificationCall.second)
        val call = arrangement.persistence.multipartCalls.single()
        assertEquals(envelopeConversationId, call.conversationId)
        assertSame(multipartEdit, call.content)
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
            MessageMultipartEditHandlerImpl(persistence, notifications),
        )
    }

    private data class Arrangement(
        val persistence: RecordingMessageEditPersistence,
        val notifications: RecordingEditNotificationManager,
        val events: MutableList<String>,
        val handler: MessageMultipartEditHandler,
    )

    private companion object {
        val olderEditInstant = Instant.parse("2026-08-19T10:15:29Z")
        val localMention = MessageMention(0, 5, originalSenderId, isSelfMention = false)
        val incomingMention = MessageMention(7, 6, otherSenderId, isSelfMention = false)
        val localAttachment = CellAssetContent(
            id = "local-asset",
            versionId = "version",
            mimeType = "image/png",
            assetPath = "local.png",
            assetSize = 10L,
            metadata = null,
            transferStatus = AssetTransferStatus.UPLOADED,
        )
        val incomingAttachment = localAttachment.copy(id = "incoming-asset", assetPath = "incoming.png")
        val multipartEdit = MessageContent.MultipartEdited(
            editMessageId = originalMessageId,
            newTextContent = "incoming body",
            newMentions = listOf(incomingMention),
            newAttachments = listOf(incomingAttachment),
        )
        val multipartMessage = signalingMessage(multipartEdit)

        fun multipartState(
            value: String? = "stored body",
            mentions: List<MessageMention> = listOf(localMention),
            attachments: List<MessageAttachment> = listOf(localAttachment),
            lastEditInstant: Instant? = olderEditInstant,
            sender: com.wire.kalium.logic.data.user.UserId = originalSenderId,
        ) = MessageEditState(
            senderUserId = sender,
            content = MessageEditState.Content.Multipart(value, mentions, attachments, lastEditInstant),
        )
    }
}

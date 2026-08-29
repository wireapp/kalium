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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.IncomingLastReadPersistence
import com.wire.kalium.logic.data.message.IsMessageSentInSelfConversationUseCase
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.data.user.UserId
import dev.mokkery.MockMode
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LastReadContentHandlerTest {

    @Test
    fun givenMessageFromAnotherUser_whenHandling_thenVerifierIsStillInvokedAndNothingIsBuffered() = runTest {
        val (arrangement, handler) = Arrangement(isSelfConversation = true).arrange()
        val message = otherUserSignalingMessage()

        handler.handle(message, lastReadContent(conversationId, latestDate))
        handler.flushPendingLastReads()

        assertEquals(listOf<Message>(message), arrangement.verifier.calls)
        assertTrue(arrangement.persistence.calls.isEmpty())
    }

    @Test
    fun givenSelfSenderOutsideSelfConversation_whenHandling_thenNothingIsBuffered() = runTest {
        val (arrangement, handler) = Arrangement(isSelfConversation = false).arrange()

        handler.handle(selfSignalingMessage(), lastReadContent(conversationId, latestDate))
        handler.flushPendingLastReads()

        assertEquals(1, arrangement.verifier.calls.size)
        assertTrue(arrangement.persistence.calls.isEmpty())
    }

    @Test
    fun givenOlderNewerEqualAndOlderDatesForOneConversation_whenFlushing_thenOnlyNewestIsPersisted() = runTest {
        val (arrangement, handler) = Arrangement(
            persistenceResult = Either.Right(mapOf(conversationId to true)),
        ).arrange()

        handler.handle(selfSignalingMessage(), lastReadContent(conversationId, olderDate))
        handler.handle(selfSignalingMessage(), lastReadContent(conversationId, latestDate))
        handler.handle(selfSignalingMessage(), lastReadContent(conversationId, latestDate))
        handler.handle(selfSignalingMessage(), lastReadContent(conversationId, oldestDate))
        handler.flushPendingLastReads()

        assertEquals(listOf(mapOf(conversationId to latestDate)), arrangement.persistence.calls)
    }

    @Test
    fun givenDistinctConversations_whenFlushing_thenOneCompleteSnapshotIsPersisted() = runTest {
        val (arrangement, handler) = Arrangement(
            persistenceResult = Either.Right(mapOf(conversationId to true, otherConversationId to true)),
        ).arrange()

        handler.handle(selfSignalingMessage(), lastReadContent(conversationId, olderDate))
        handler.handle(selfSignalingMessage(), lastReadContent(otherConversationId, latestDate))
        handler.flushPendingLastReads()

        assertEquals(
            listOf(mapOf(conversationId to olderDate, otherConversationId to latestDate)),
            arrangement.persistence.calls,
        )
    }

    @Test
    fun givenEmptyAndRepeatedFlushes_whenFlushing_thenPersistenceIsCalledOnlyForThePopulatedSnapshot() = runTest {
        val (arrangement, handler) = Arrangement(
            persistenceResult = Either.Right(mapOf(conversationId to true)),
        ).arrange()

        handler.flushPendingLastReads()
        handler.handle(selfSignalingMessage(), lastReadContent(conversationId, latestDate))
        handler.flushPendingLastReads()
        handler.flushPendingLastReads()

        assertEquals(listOf(mapOf(conversationId to latestDate)), arrangement.persistence.calls)
    }

    @Test
    fun givenPersistenceResults_whenFlushing_thenNotificationsAreScheduledOnlyForFalseEntriesInReturnedOrder() = runTest {
        val returnedConversationId = ConversationId("returned-conversation", "wire.example")
        val (arrangement, handler) = Arrangement(
            persistenceResult = Either.Right(
                mapOf(
                    conversationId to false,
                    otherConversationId to true,
                    returnedConversationId to false,
                )
            ),
        ).arrange()

        handler.handle(selfSignalingMessage(), lastReadContent(conversationId, latestDate))
        handler.flushPendingLastReads()

        verifySuspend(VerifyMode.order) {
            arrangement.notificationEventsManager.scheduleConversationSeenNotification(eq(conversationId))
            arrangement.notificationEventsManager.scheduleConversationSeenNotification(eq(returnedConversationId))
        }
        verifySuspend(VerifyMode.not) {
            arrangement.notificationEventsManager.scheduleConversationSeenNotification(eq(otherConversationId))
        }
    }

    @Test
    fun givenReturnedPersistenceFailure_whenFlushingAgain_thenClearedSnapshotIsNotRetriedAndNoNotificationIsScheduled() = runTest {
        val (arrangement, handler) = Arrangement(
            persistenceResult = Either.Left(StorageFailure.DataNotFound),
        ).arrange()

        handler.handle(selfSignalingMessage(), lastReadContent(conversationId, latestDate))
        handler.flushPendingLastReads()
        handler.flushPendingLastReads()

        assertEquals(listOf(mapOf(conversationId to latestDate)), arrangement.persistence.calls)
        verifySuspend(VerifyMode.not) {
            arrangement.notificationEventsManager.scheduleConversationSeenNotification(eq(conversationId))
        }
    }

    @Test
    fun givenPersistenceCancellation_whenFlushing_thenCancellationEscapesAndClearedSnapshotIsNotRetried() = runTest {
        val expected = CancellationException("last-read flush cancelled")
        val (arrangement, handler) = Arrangement(persistenceThrowable = expected).arrange()
        handler.handle(selfSignalingMessage(), lastReadContent(conversationId, latestDate))

        val actual = assertFailsWith<CancellationException> { handler.flushPendingLastReads() }
        handler.flushPendingLastReads()

        assertSame(expected, actual)
        assertEquals(listOf(mapOf(conversationId to latestDate)), arrangement.persistence.calls)
    }

    @Test
    fun givenNotificationFailure_whenFlushing_thenFailureEscapesAndLaterNotificationsAreSkipped() = runTest {
        val expected = IllegalStateException("notification failed")
        val (arrangement, handler) = Arrangement(
            persistenceResult = Either.Right(mapOf(conversationId to false, otherConversationId to false)),
        ).arrange()
        everySuspend {
            arrangement.notificationEventsManager.scheduleConversationSeenNotification(eq(conversationId))
        } throws expected
        handler.handle(selfSignalingMessage(), lastReadContent(conversationId, latestDate))

        val actual = assertFailsWith<IllegalStateException> { handler.flushPendingLastReads() }

        assertSame(expected, actual)
        verifySuspend(VerifyMode.not) {
            arrangement.notificationEventsManager.scheduleConversationSeenNotification(eq(otherConversationId))
        }
    }

    private class Arrangement(
        isSelfConversation: Boolean = true,
        persistenceResult: Either<StorageFailure, Map<ConversationId, Boolean>> = Either.Right(emptyMap()),
        persistenceThrowable: Throwable? = null,
    ) {
        val verifier = RecordingSelfConversationVerifier(isSelfConversation)
        val persistence = RecordingIncomingLastReadPersistence(persistenceResult, persistenceThrowable)
        val notificationEventsManager = mock<NotificationEventsManager>(mode = MockMode.autoUnit)

        fun arrange() = this to LastReadContentHandlerImpl(
            incomingLastReadPersistence = persistence,
            selfUserId = selfUserId,
            isMessageSentInSelfConversation = verifier,
            notificationEventsManager = notificationEventsManager,
        )
    }

    private class RecordingSelfConversationVerifier(
        private val result: Boolean,
    ) : IsMessageSentInSelfConversationUseCase {
        val calls = mutableListOf<Message>()

        override suspend fun invoke(message: Message): Boolean {
            calls += message
            return result
        }
    }

    private class RecordingIncomingLastReadPersistence(
        private val result: Either<StorageFailure, Map<ConversationId, Boolean>>,
        private val throwable: Throwable?,
    ) : IncomingLastReadPersistence {
        val calls = mutableListOf<Map<ConversationId, Instant>>()

        override suspend fun updateReadDatesAndGetHasUnreadEvents(
            conversationDates: Map<ConversationId, Instant>,
        ): Either<StorageFailure, Map<ConversationId, Boolean>> {
            calls += conversationDates
            throwable?.let { throw it }
            return result
        }
    }

    private companion object {
        val selfUserId = UserId("self-user", "wire.com")
        val otherUserId = UserId("other-user", "wire.com")
        val conversationId = ConversationId("conversation", "wire.com")
        val otherConversationId = ConversationId("other-conversation", "wire.com")
        val oldestDate = Instant.parse("2026-02-10T11:59:58Z")
        val olderDate = Instant.parse("2026-02-10T11:59:59Z")
        val latestDate = Instant.parse("2026-02-10T12:00:00Z")

        fun selfSignalingMessage() = Message.Signaling(
            id = "signaling-id",
            content = lastReadContent(conversationId, latestDate),
            conversationId = conversationId,
            date = latestDate,
            senderUserId = selfUserId,
            senderClientId = ClientId("self-client"),
            status = Message.Status.Sent,
            isSelfMessage = true,
            expirationData = null,
        )

        fun otherUserSignalingMessage() = selfSignalingMessage().copy(
            senderUserId = otherUserId,
            isSelfMessage = false,
        )

        fun lastReadContent(conversationId: ConversationId, timestamp: Instant) = MessageContent.LastRead(
            messageId = "message-id",
            conversationId = conversationId,
            time = timestamp,
        )
    }
}

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
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.feature.message.MessageOperationResult
import com.wire.kalium.logic.feature.message.receipt.ConversationWorkQueue
import com.wire.kalium.logic.feature.message.receipt.InstantConversationWorkQueue
import com.wire.kalium.logic.feature.message.receipt.ParallelConversationWorkQueue
import com.wire.kalium.logic.feature.message.receipt.SendConfirmationUseCase
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.messaging.sending.MessageTarget
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class UpdateConversationReadDateUseCaseTest {

    @Test
    fun givenReadQueuedBeforeManualUnread_whenWorkRuns_thenDoesNotClearMarkerOrSync() = runTest {
        val operationTime = Instant.parse("2026-02-10T12:00:00Z")
        val conversation = TestConversation.CONVERSATION.copy(lastReadDate = operationTime - 10.seconds)
        lateinit var queuedWork: suspend () -> Unit
        val (arrangement, subject) = arrange {
            withConversationReturning(conversation)
            clock = fixedClock(operationTime)
            workQueue = ConversationWorkQueue { input, worker -> queuedWork = { worker.doWork(input) } }
        }

        subject(conversation.id, operationTime)
        arrangement.withConversationReturning(conversation.copy(
            manualUnreadDate = operationTime - 5.seconds,
            manualUnreadUpdatedAt = operationTime + 1.milliseconds,
        ))
        queuedWork()

        verifySuspend(VerifyMode.not) { arrangement.conversationRepository.clearManualUnreadMarker(any(), any(), any()) }
        verifySuspend(VerifyMode.not) { arrangement.conversationRepository.updateConversationReadDate(any(), any()) }
        verifySuspend(VerifyMode.not) { arrangement.sendConfirmation(any(), any(), any()) }
        verifySuspend(VerifyMode.not) { arrangement.messageSender.sendMessage(any(), any()) }
    }

    @Test
    fun givenManualUnreadWithEqualCanonicalRead_whenReadingAgain_thenClearsAndSyncsWithoutRepeatingReceipts() = runTest {
        val operationTime = Instant.parse("2026-02-10T12:00:00Z")
        val conversation = TestConversation.CONVERSATION.copy(
            lastReadDate = operationTime - 1.seconds,
            manualUnreadDate = operationTime - 10.seconds,
            manualUnreadUpdatedAt = operationTime - 1.milliseconds,
        )
        val (arrangement, subject) = arrange {
            withConversationReturning(conversation)
            clock = fixedClock(operationTime)
        }

        subject(conversation.id, conversation.lastReadDate)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.clearManualUnreadMarker(conversation.id, conversation.lastReadDate, operationTime)
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(matches {
                val content = it.content
                content is MessageContent.LastRead && content.conversationId == conversation.id &&
                    content.time == conversation.lastReadDate && content.operationTime == operationTime
            }, any())
        }
        verifySuspend(VerifyMode.not) { arrangement.conversationRepository.updateConversationReadDate(any(), any()) }
        verifySuspend(VerifyMode.not) { arrangement.sendConfirmation(any(), any(), any()) }
    }

    @Test
    fun givenReadThroughManualBoundaryBelowCanonical_whenReadingAgain_thenClearsAndSyncsWithoutRegressingCanonical() = runTest {
        val operationTime = Instant.parse("2026-02-10T12:00:00Z")
        val boundary = operationTime - 10.seconds
        val conversation = TestConversation.CONVERSATION.copy(
            lastReadDate = operationTime - 1.seconds,
            manualUnreadDate = boundary,
            manualUnreadUpdatedAt = operationTime - 1.milliseconds,
        )
        val (arrangement, subject) = arrange {
            withConversationReturning(conversation)
            clock = fixedClock(operationTime)
        }

        subject(conversation.id, boundary)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.clearManualUnreadMarker(conversation.id, boundary, operationTime)
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(matches {
                val content = it.content
                content is MessageContent.LastRead && content.conversationId == conversation.id &&
                    content.time == boundary && content.operationTime == operationTime
            }, any())
        }
        verifySuspend(VerifyMode.not) { arrangement.conversationRepository.updateConversationReadDate(any(), any()) }
        verifySuspend(VerifyMode.not) { arrangement.sendConfirmation(any(), any(), any()) }
    }

    @Test
    fun givenCurrentStoredLastReadDateIsNewerThanEnqueued_whenWorking_thenShouldNotTryToDoAnyWork() = runTest {
        val persistedLastRead = Clock.System.now()
        val conversationId = TestConversation.CONVERSATION.id
        val (arrangement, updateConversationReadDateUseCase) = arrange {
            withLastReadDateReturning(conversationId, persistedLastRead)
        }

        updateConversationReadDateUseCase(conversationId, persistedLastRead - 1.seconds)

        verifySuspend(VerifyMode.not) {
            arrangement.sendConfirmation(any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.conversationRepository.updateConversationReadDate(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.messageSender.sendMessage(any(), any())
        }
    }

    @Test
    fun givenLastReadWasAlreadyPersistedLocally_whenQueuedWorkRuns_thenShouldSyncOtherSelfClients() = runTest {
        val lastRead = Clock.System.now()
        val conversationId = TestConversation.CONVERSATION.id
        val (arrangement, updateConversationReadDateUseCase) = arrange {
            withLastReadDateReturning(conversationId, lastRead)
        }

        updateConversationReadDateUseCase(conversationId, lastRead)

        verifySuspend(VerifyMode.not) {
            arrangement.sendConfirmation(any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.conversationRepository.updateConversationReadDate(any(), any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(
                message = matches { message ->
                    val content = message.content
                    assertIs<MessageContent.LastRead>(content)
                    assertEquals(conversationId, content.conversationId)
                    assertEquals(lastRead, content.time)
                    true
                },
                messageTarget = any()
            )
        }
    }

    @Test
    fun givenProvidedTimeIsNewerThanPersistedLastReadForConversation_whenWorking_thenShouldTryToSendReceipts() = runTest {
        val persistedLastRead = Clock.System.now()
        val newLastRead = persistedLastRead + 1.seconds
        val conversationId = TestConversation.CONVERSATION.id
        val (arrangement, updateConversationReadDateUseCase) = arrange {
            withLastReadDateReturning(conversationId, persistedLastRead)
        }

        updateConversationReadDateUseCase(conversationId, newLastRead)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.sendConfirmation(conversationId, persistedLastRead, newLastRead)
        }
    }

    @Test
    fun givenProvidedTimeIsNewerThanPersistedLastReadForConversation_whenWorking_thenShouldUpdateLastReadLocally() = runTest {
        val persistedLastRead = Clock.System.now()
        val newLastRead = persistedLastRead + 1.seconds
        val conversationId = TestConversation.CONVERSATION.id
        val (arrangement, updateConversationReadDateUseCase) = arrange {
            withLastReadDateReturning(conversationId, persistedLastRead)
        }

        updateConversationReadDateUseCase(conversationId, newLastRead)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.updateConversationReadDate(conversationId, newLastRead)
        }
    }

    @Test
    fun givenProvidedTimeIsNewerThanPersistedLastReadForConversation_whenWorking_thenShouldUpdateLastReadForOtherSelfClients() = runTest {
        val persistedLastRead = Clock.System.now()
        val newLastRead = persistedLastRead + 1.seconds
        val conversationId = TestConversation.CONVERSATION.id
        val (arrangement, updateConversationReadDateUseCase) = arrange {
            withLastReadDateReturning(conversationId, persistedLastRead)
        }

        updateConversationReadDateUseCase(conversationId, newLastRead)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(
                message = matches { message ->
                    val content = message.content
                    assertIs<MessageContent.LastRead>(content)
                    assertEquals(conversationId, content.conversationId)
                    assertEquals(newLastRead, content.time)
                    assertEquals(arrangement.selfConversationId, message.conversationId)
                    true
                },
                messageTarget = matches { target ->
                    assertIs<MessageTarget.Conversation>(target)
                    true
                }
            )
        }
    }

    @Test
    fun givenMLSGroupConversation_whenUpdatingLastRead_thenShouldPersistAndSyncWithoutSendingReceipt() = runTest {
        val persistedLastRead = Clock.System.now()
        val newLastRead = persistedLastRead + 1.seconds
        val conversation = TestConversation.MLS_CONVERSATION.copy(
            type = Conversation.Type.Group.Regular,
            lastReadDate = persistedLastRead
        )
        val (arrangement, updateConversationReadDateUseCase) = arrange {
            withLastReadDateReturning(conversation.id, persistedLastRead)
            withRealSendConfirmation(conversation)
        }

        updateConversationReadDateUseCase(conversation.id, newLastRead)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.updateConversationReadDate(conversation.id, newLastRead)
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(
                message = matches { it.content is MessageContent.LastRead },
                messageTarget = any()
            )
        }
        verifySuspend(VerifyMode.not) {
            arrangement.messageSender.sendMessage(
                message = matches { it.content is MessageContent.Receipt },
                messageTarget = any()
            )
        }
        verifySuspend(VerifyMode.not) {
            arrangement.receiptMessageRepository.getPendingConfirmationMessagesByConversationAfterDate(
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun givenProvidedTimeIsNewer_whenInvokedImmediately_thenSendConfirmationIsCalled() = runTest {
        val persistedLastRead = Clock.System.now()
        val newLastRead = persistedLastRead + 1.seconds
        val conversationId = TestConversation.CONVERSATION.id
        val (arrangement, updateConversationReadDateUseCase) = arrange {
            withLastReadDateReturning(conversationId, persistedLastRead)
        }

        updateConversationReadDateUseCase(conversationId, newLastRead, invokeImmediately = true)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.sendConfirmation(conversationId, persistedLastRead, newLastRead)
        }
    }

    @Test
    fun givenStoredLastReadDateIsNewer_whenInvokedImmediately_thenSendConfirmationIsNotCalled() = runTest {
        val persistedLastRead = Clock.System.now()
        val conversationId = TestConversation.CONVERSATION.id
        val (arrangement, updateConversationReadDateUseCase) = arrange {
            withLastReadDateReturning(conversationId, persistedLastRead)
        }

        updateConversationReadDateUseCase(conversationId, persistedLastRead - 1.seconds, invokeImmediately = true)

        verifySuspend(VerifyMode.not) {
            arrangement.sendConfirmation(any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.conversationRepository.updateConversationReadDate(any(), any())
        }
    }

    @Test
    fun givenAnyCall_whenInvoking_thenShouldEnqueueWork() = runTest {
        val expectedId = TestConversation.CONVERSATION.id.copy(value = "potato")
        val expectedTime = Clock.System.now()
        lateinit var enqueuedId: ConversationId
        lateinit var enqueuedInstant: Instant
        var enqueuedTimes = 0
        val (_, updateConversationReadDateUseCase) = arrange {
            withLastReadDateReturning(expectedId, expectedTime)
            workQueue = ConversationWorkQueue { input, _ ->
                enqueuedTimes += 1
                enqueuedInstant = input.eventTime
                enqueuedId = input.conversationId
            }
        }
        updateConversationReadDateUseCase(expectedId, expectedTime)

        assertEquals(1, enqueuedTimes)
        assertEquals(expectedId, enqueuedId)
        assertEquals(expectedTime, enqueuedInstant)
    }

    @Test
    fun givenCallerCancelledAfterEnqueue_whenQueued_thenWorkStillExecutes() = runTest {
        val persistedLastRead = Clock.System.now()
        val newLastRead = persistedLastRead + 1.seconds
        val conversationId = TestConversation.CONVERSATION.id
        val workQueue = ParallelConversationWorkQueue(backgroundScope, kaliumLogger, StandardTestDispatcher(testScheduler))
        val (arrangement, updateConversationReadDateUseCase) = arrange {
            withLastReadDateReturning(conversationId, persistedLastRead)
            this.workQueue = workQueue
        }

        val job = launch {
            updateConversationReadDateUseCase(conversationId, newLastRead)
        }
        runCurrent()
        job.cancel()

        advanceTimeBy(3.seconds + 1.milliseconds)
        runCurrent()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.updateConversationReadDate(conversationId, newLastRead)
        }
    }

    private class Arrangement(
        private val configure: suspend Arrangement.() -> Unit
    ) {

        var currentClientId = TestClient.CLIENT_ID
        var selfUserID = TestUser.SELF.id
        var selfConversationId = TestConversation.SELF().id.copy("SELF")
        val selfConversationIdProvider = mock<SelfConversationIdProvider>(mode = MockMode.autoUnit)
        val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)
        val messageSender = mock<MessageSender>(mode = MockMode.autoUnit)
        val receiptMessageRepository = mock<MessageRepository>(mode = MockMode.autoUnit)
        private val syncManager = mock<SyncManager>(mode = MockMode.autoUnit)
        private val userPropertyRepository = mock<UserPropertyRepository>(mode = MockMode.autoUnit)
        var sendConfirmation: SendConfirmationUseCase = mock<SendConfirmationUseCase>(mode = MockMode.autoUnit)

        var workQueue: ConversationWorkQueue = InstantConversationWorkQueue()
        var clock: Clock = Clock.System

        suspend fun arrange(): Pair<Arrangement, UpdateConversationReadDateUseCase> = run {
            everySuspend {
                sendConfirmation(any(), any(), any())
            } returns MessageOperationResult.Success
            everySuspend {
                conversationRepository.updateConversationReadDate(any(), any())
            } returns Either.Right(Unit)
            everySuspend {
                conversationRepository.clearManualUnreadMarker(any(), any(), any())
            } returns Either.Right(Unit)
            everySuspend {
                messageSender.sendMessage(any(), any())
            } returns Either.Right(Unit)
            withSelfConversationIds(listOf(selfConversationId))
            configure()
            this@Arrangement to UpdateConversationReadDateUseCase(
                conversationRepository,
                messageSender,
                { Either.Right(currentClientId) },
                selfUserID,
                selfConversationIdProvider,
                sendConfirmation,
                workQueue,
                logger = kaliumLogger,
                clock = clock,
            )
        }

        suspend fun withLastReadDateReturning(conversationId: ConversationId, lastReadDate: Instant) {
            everySuspend {
                conversationRepository.getConversationLastReadDate(conversationId)
            } returns Either.Right(lastReadDate)
            everySuspend {
                conversationRepository.getConversationById(conversationId)
            } returns Either.Right(TestConversation.CONVERSATION.copy(id = conversationId, lastReadDate = lastReadDate))
        }

        suspend fun withConversationReturning(conversation: Conversation) {
            everySuspend { conversationRepository.getConversationById(conversation.id) } returns Either.Right(conversation)
        }

        suspend fun withRealSendConfirmation(conversation: Conversation) {
            everySuspend {
                conversationRepository.getConversationById(conversation.id)
            } returns Either.Right(conversation)
            sendConfirmation = SendConfirmationUseCase(
                currentClientIdProvider = { Either.Right(currentClientId) },
                syncManager = syncManager,
                messageSender = messageSender,
                selfUserId = selfUserID,
                conversationRepository = conversationRepository,
                messageRepository = receiptMessageRepository,
                userPropertyRepository = userPropertyRepository
            )
        }

        suspend fun withSelfConversationIds(conversationIds: List<ConversationId>) {
            everySuspend { selfConversationIdProvider.invoke() } returns Either.Right(conversationIds)
        }
    }

    private companion object {
        suspend fun arrange(configure: suspend Arrangement.() -> Unit) = Arrangement(configure).arrange()

        fun fixedClock(instant: Instant) = object : Clock {
            override fun now() = instant
        }
    }
}

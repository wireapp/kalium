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
import com.wire.kalium.logic.feature.message.MessageOperationResult
import com.wire.kalium.logic.feature.message.receipt.ConversationWorkQueue
import com.wire.kalium.logic.feature.message.receipt.InstantConversationWorkQueue
import com.wire.kalium.logic.feature.message.receipt.ParallelConversationWorkQueue
import com.wire.kalium.logic.feature.message.receipt.SendConfirmationUseCase
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.messaging.sending.MessageTarget
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matching
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class UpdateConversationReadDateUseCaseTest {

    @Test
    fun givenCurrentStoredLastReadDateIsNewerThanEnqueued_whenWorking_thenShouldNotTryToDoAnyWork() = runTest {
        val persistedLastRead = Clock.System.now()
        val conversationId = TestConversation.CONVERSATION.id
        val (arrangement, updateConversationReadDateUseCase) = arrange {
            withObserveByIdReturning(
                TestConversation.CONVERSATION.copy(lastReadDate = persistedLastRead)
            )
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
    fun givenProvidedTimeIsNewerThanPersistedLastReadForConversation_whenWorking_thenShouldTryToSendReceipts() = runTest {
        val persistedLastRead = Clock.System.now()
        val newLastRead = persistedLastRead + 1.seconds
        val conversationId = TestConversation.CONVERSATION.id
        val (arrangement, updateConversationReadDateUseCase) = arrange {
            withObserveByIdReturning(
                TestConversation.CONVERSATION.copy(lastReadDate = persistedLastRead)
            )
        }

        updateConversationReadDateUseCase(conversationId, newLastRead)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.sendConfirmation(eq(conversationId), eq(persistedLastRead), eq(newLastRead))
        }
    }

    @Test
    fun givenProvidedTimeIsNewerThanPersistedLastReadForConversation_whenWorking_thenShouldUpdateLastReadLocally() = runTest {
        val persistedLastRead = Clock.System.now()
        val newLastRead = persistedLastRead + 1.seconds
        val conversationId = TestConversation.CONVERSATION.id
        val (arrangement, updateConversationReadDateUseCase) = arrange {
            withObserveByIdReturning(
                TestConversation.CONVERSATION.copy(lastReadDate = persistedLastRead)
            )
        }

        updateConversationReadDateUseCase(conversationId, newLastRead)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.updateConversationReadDate(eq(conversationId), eq(newLastRead))
        }
    }

    @Test
    fun givenProvidedTimeIsNewerThanPersistedLastReadForConversation_whenWorking_thenShouldNotifyLastReadHook() = runTest {
        val persistedLastRead = Clock.System.now()
        val newLastRead = persistedLastRead + 1.seconds
        val conversationId = TestConversation.CONVERSATION.id
        val (arrangement, updateConversationReadDateUseCase) = arrange {
            withObserveByIdReturning(
                TestConversation.CONVERSATION.copy(lastReadDate = persistedLastRead)
            )
        }

        updateConversationReadDateUseCase(conversationId, newLastRead)

        // Hook invocation is covered by integration tests; persistence notifier here is a no-op.
    }

    @Test
    fun givenProvidedTimeIsNewerThanPersistedLastReadForConversation_whenWorking_thenShouldUpdateLastReadForOtherSelfClients() = runTest {
        val persistedLastRead = Clock.System.now()
        val newLastRead = persistedLastRead + 1.seconds
        val conversationId = TestConversation.CONVERSATION.id
        val (arrangement, updateConversationReadDateUseCase) = arrange {
            withObserveByIdReturning(
                TestConversation.CONVERSATION.copy(lastReadDate = persistedLastRead)
            )
        }

        updateConversationReadDateUseCase(conversationId, newLastRead)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(
                message = matching { message ->
                    val content = message.content
                    assertIs<MessageContent.LastRead>(content)
                    assertEquals(conversationId, content.conversationId)
                    assertEquals(newLastRead, content.time)
                    assertEquals(arrangement.selfConversationId, message.conversationId)
                    true
                },
                messageTarget = matching { target ->
                    assertIs<MessageTarget.Conversation>(target)
                    true
                }
            )
        }
    }

    @Test
    fun givenProvidedTimeIsNewer_whenInvokedImmediately_thenSendConfirmationIsCalled() = runTest {
        val persistedLastRead = Clock.System.now()
        val newLastRead = persistedLastRead + 1.seconds
        val conversationId = TestConversation.CONVERSATION.id
        val (arrangement, updateConversationReadDateUseCase) = arrange {
            withObserveByIdReturning(
                TestConversation.CONVERSATION.copy(lastReadDate = persistedLastRead)
            )
        }

        updateConversationReadDateUseCase(conversationId, newLastRead, invokeImmediately = true)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.sendConfirmation(eq(conversationId), eq(persistedLastRead), eq(newLastRead))
        }
    }

    @Test
    fun givenStoredLastReadDateIsNewer_whenInvokedImmediately_thenSendConfirmationIsNotCalled() = runTest {
        val persistedLastRead = Clock.System.now()
        val conversationId = TestConversation.CONVERSATION.id
        val (arrangement, updateConversationReadDateUseCase) = arrange {
            withObserveByIdReturning(
                TestConversation.CONVERSATION.copy(lastReadDate = persistedLastRead)
            )
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
            withObserveByIdReturning(
                TestConversation.CONVERSATION.copy(lastReadDate = persistedLastRead)
            )
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
            arrangement.conversationRepository.updateConversationReadDate(eq(conversationId), eq(newLastRead))
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
        val sendConfirmation = mock<SendConfirmationUseCase>(mode = MockMode.autoUnit)
        val persistenceEventHookNotifier = object : PersistenceEventHookNotifier {}

        var workQueue: ConversationWorkQueue = InstantConversationWorkQueue()

        suspend fun arrange(): Pair<Arrangement, UpdateConversationReadDateUseCase> = run {
            everySuspend {
                sendConfirmation(any(), any(), any())
            } returns MessageOperationResult.Success
            everySuspend {
                conversationRepository.updateConversationReadDate(any(), any())
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
                persistenceEventHookNotifier,
                kaliumLogger
            )
        }

        suspend fun withObserveByIdReturning(conversation: Conversation) {
            everySuspend {
                conversationRepository.observeConversationById(eq(conversation.id))
            } returns flowOf(Either.Right(conversation))
        }

        suspend fun withSelfConversationIds(conversationIds: List<ConversationId>) {
            everySuspend { selfConversationIdProvider.invoke() } returns Either.Right(conversationIds)
        }
    }

    private companion object {
        suspend fun arrange(configure: suspend Arrangement.() -> Unit) = Arrangement(configure).arrange()
    }
}

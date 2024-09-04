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
package com.wire.kalium.logic.feature.message.confirmation

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.right
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.SyncManager
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ConfirmationDeliveryHandlerTest {

    @Test
    fun givenANewMessage_whenEnqueuing_thenShouldBeAddedSuccessfullyToTheConversationKey() = runTest {
        val (arrangement, sut) = Arrangement()
            .withCurrentClientIdProvider()
            .arrange()

        sut.enqueueConfirmationDelivery(TestConversation.ID, TestMessage.TEST_MESSAGE_ID)

        assertTrue(arrangement.pendingConfirmationMessages.containsKey(TestConversation.ID))
        assertTrue(arrangement.pendingConfirmationMessages.values.flatten().contains(TestMessage.TEST_MESSAGE_ID))
    }

    @Test
    fun givenANewMessage_whenEnqueuingDuplicated_thenShouldNotBeAddedToTheConversationKey() = runTest {
        val (arrangement, sut) = Arrangement()
            .withCurrentClientIdProvider()
            .arrange()

        sut.enqueueConfirmationDelivery(TestConversation.ID, TestMessage.TEST_MESSAGE_ID)
        sut.enqueueConfirmationDelivery(TestConversation.ID, TestMessage.TEST_MESSAGE_ID)

        assertTrue(arrangement.pendingConfirmationMessages.containsKey(TestConversation.ID))
        assertEquals(1, arrangement.pendingConfirmationMessages.values.flatten().filter { it == TestMessage.TEST_MESSAGE_ID }.size)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenMessagesEnqueued_whenCollectingThem_thenShouldSendOnlyForOneToOneConversations() = runTest {
        val (arrangement, sut) = Arrangement()
            .withCurrentClientIdProvider()
            .withConversationDetailsResult(flowOf(TestConversation.CONVERSATION.right()))
            .withMessageSenderResult()
            .arrange()

        val job = launch { sut.sendPendingConfirmations() }
        advanceUntilIdle()

        sut.enqueueConfirmationDelivery(TestConversation.ID, TestMessage.TEST_MESSAGE_ID)
        advanceUntilIdle()
        job.cancel()

        coVerify { arrangement.conversationRepository.observeConversationById(any()) }.wasInvoked()
        coVerify { arrangement.messageSender.sendMessage(any(), any()) }.wasInvoked()
        assertTrue(arrangement.pendingConfirmationMessages.isEmpty())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenMessagesEnqueued_whenCollectingThemAndNoSession_thenShouldStopCollecting() = runTest {
        val (arrangement, sut) = Arrangement()
            .withCurrentClientIdProvider()
            .withConversationDetailsResult(flowOf(TestConversation.CONVERSATION.right()))
            .withMessageSenderResult()
            .arrange()

        val job = launch { sut.sendPendingConfirmations() }
        advanceUntilIdle()

        job.cancel()

        sut.enqueueConfirmationDelivery(TestConversation.ID, TestMessage.TEST_MESSAGE_ID)
        advanceUntilIdle()

        coVerify { arrangement.conversationRepository.observeConversationById(any()) }.wasNotInvoked()
        coVerify { arrangement.messageSender.sendMessage(any(), any()) }.wasNotInvoked()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenMessagesEnqueued_whenSendingConfirmationsAndError_thenShouldNotFail() = runTest {
        val (arrangement, sut) = Arrangement()
            .withCurrentClientIdProvider()
            .withConversationDetailsResult(flowOf(TestConversation.CONVERSATION.right()))
            .withMessageSenderResult(Either.Left(CoreFailure.Unknown(RuntimeException("Something went wrong"))))
            .arrange()

        val job = launch { sut.sendPendingConfirmations() }
        advanceUntilIdle()

        sut.enqueueConfirmationDelivery(TestConversation.ID, TestMessage.TEST_MESSAGE_ID)
        advanceUntilIdle()

        job.cancel()

        coVerify { arrangement.conversationRepository.observeConversationById(any()) }.wasInvoked()
        coVerify { arrangement.messageSender.sendMessage(any(), any()) }.wasInvoked()
        assertTrue(arrangement.pendingConfirmationMessages.isEmpty())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenABigLoadOfMessagesEnqueued_whenSendingConfirmations_thenShouldAddAndRemoveSecurely() = runTest {
        val (arrangement, sut) = Arrangement()
            .withCurrentClientIdProvider()
            .withConversationDetailsResult(flowOf(TestConversation.CONVERSATION.right()))
            .withMessageSenderResult()
            .arrange()

        val job = launch { sut.sendPendingConfirmations() }
        advanceUntilIdle()

        val messagesCount = 500
        launch {
            repeat(messagesCount) { sut.enqueueConfirmationDelivery(TestConversation.ID, uuid4().toString()) }
            delay(2000)
        }
        advanceTimeBy(1000L)
        launch {
            repeat(messagesCount) { sut.enqueueConfirmationDelivery(TestConversation.ID, uuid4().toString()) }
            delay(2000)
        }
        advanceTimeBy(2000L)
        job.cancel()

        coVerify { arrangement.conversationRepository.observeConversationById(any()) }.wasInvoked()
        coVerify { arrangement.messageSender.sendMessage(any(), any()) }.wasInvoked(once)
        assertTrue(arrangement.pendingConfirmationMessages.isEmpty())
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenMultipleEnqueues_whenSendingConfirmations_thenShouldOnlySendOnce() = runTest {
        val (arrangement, sut) = Arrangement()
            .withCurrentClientIdProvider()
            .withConversationDetailsResult(flowOf(TestConversation.CONVERSATION.right()))
            .withMessageSenderResult()
            .arrange()

        val job = launch { sut.sendPendingConfirmations() }
        advanceUntilIdle()

        repeat(100) {
            sut.enqueueConfirmationDelivery(TestConversation.ID, uuid4().toString())
        }
        advanceUntilIdle()
        job.cancel()

        coVerify { arrangement.conversationRepository.observeConversationById(any()) }.wasInvoked()
        coVerify { arrangement.messageSender.sendMessage(any(), any()) }.wasInvoked(once)
        assertTrue(arrangement.pendingConfirmationMessages.isEmpty())
    }

    @Test
    fun givenSyncIsOngoing_whenItTakesLongTimeToExecute_thenShouldReturnAnyway() = runTest {
        val (arrangement, handler) = Arrangement()
            .withCurrentClientIdProvider()
            .withConversationDetailsResult(flowOf(TestConversation.CONVERSATION.right()))
            .withMessageSenderResult()
            .arrange()

        coEvery { arrangement.syncManager.waitUntilLive() }.invokes { ->
            delay(10.seconds)
        }

        val sendJob = launch(Dispatchers.Default) {
            handler.sendPendingConfirmations()
        }
        advanceTimeBy(1.seconds)

        val enqueueJob = launch {
            handler.enqueueConfirmationDelivery(
                TestConversation.ID,
                TestMessage.TEST_MESSAGE_ID
            )
        }
        advanceTimeBy(1.seconds) // Enqueue and return immediately

        assertTrue(enqueueJob.isCompleted)
        sendJob.cancel()
    }

    private class Arrangement {

        @Mock
        private val currentClientIdProvider = mock(CurrentClientIdProvider::class)

        @Mock
        val syncManager: SyncManager = mock(SyncManager::class)

        @Mock
        val messageSender = mock(MessageSender::class)

        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        val pendingConfirmationMessages: MutableMap<ConversationId, MutableSet<String>> = mutableMapOf()

        suspend fun withCurrentClientIdProvider() = apply {
            coEvery { currentClientIdProvider.invoke() }.returns(Either.Right(TestClient.CLIENT_ID))
        }

        suspend fun withConversationDetailsResult(result: Flow<Either<StorageFailure, Conversation>>) = apply {
            coEvery { conversationRepository.observeConversationById(any()) }.returns(result)
        }

        suspend fun withMessageSenderResult(result: Either<CoreFailure, Unit> = Unit.right()) = apply {
            coEvery { messageSender.sendMessage(any(), any()) }.returns(result)
        }

        fun arrange() = this to ConfirmationDeliveryHandlerImpl(
            syncManager = syncManager,
            selfUserId = TestUser.SELF.id,
            currentClientIdProvider = currentClientIdProvider,
            conversationRepository = conversationRepository,
            messageSender = messageSender,
            kaliumLogger = kaliumLogger,
            pendingConfirmationMessages = pendingConfirmationMessages
        )
    }

}

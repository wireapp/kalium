package com.wire.kalium.logic.sync

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.ConfigurationApi
import io.mockative.Mock
import io.mockative.any
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ConfigurationApi::class, ExperimentalCoroutinesApi::class)
class PendingMessagesSenderWorkerTest {

    @Mock
    val messageRepository = configure(mock(MessageRepository::class)) { stubsUnitByDefault = true }

    @Mock
    val messageSender = configure(mock(MessageSender::class)) { stubsUnitByDefault = true }

    private lateinit var pendingMessagesSenderWorker: PendingMessagesSenderWorker

    @BeforeTest
    fun setup() {
        pendingMessagesSenderWorker = PendingMessagesSenderWorker(messageRepository, messageSender, TestUser.USER_ID)
    }

    @Test
    fun givenPendingMessagesAreFetched_whenExecutingAWorker_thenScheduleSendingOfMessages() = runTest {
        val message = TestMessage.TEXT_MESSAGE
        given(messageRepository)
            .suspendFunction(messageRepository::getAllPendingMessagesFromUser)
            .whenInvokedWith(eq(TestUser.USER_ID))
            .thenReturn(Either.Right(listOf(message)))
        given(messageSender)
            .suspendFunction(messageSender::trySendingOutgoingMessageById)
            .whenInvokedWith(eq(message.conversationId), eq(message.id))
            .thenReturn(Either.Right(Unit))

        pendingMessagesSenderWorker.doWork()

        verify(messageSender)
            .suspendFunction(messageSender::trySendingOutgoingMessageById)
            .with(eq(message.conversationId), eq(message.id))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenPendingMessagesReturnsFailure_whenExecutingAWorker_thenDoNothing() = runTest {
        val dataNotFoundFailure = StorageFailure.DataNotFound
        given(messageRepository)
            .suspendFunction(messageRepository::getAllPendingMessagesFromUser)
            .whenInvokedWith(eq(TestUser.USER_ID))
            .thenReturn(Either.Left(dataNotFoundFailure))

        pendingMessagesSenderWorker.doWork()

        verify(messageSender)
            .suspendFunction(messageSender::trySendingOutgoingMessageById)
            .with(any(), any())
            .wasNotInvoked()
    }
}

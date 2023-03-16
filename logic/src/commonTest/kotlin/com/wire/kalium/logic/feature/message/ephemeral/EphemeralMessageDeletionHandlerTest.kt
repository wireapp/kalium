package com.wire.kalium.logic.feature.message.ephemeral

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.Times
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class EphemeralMessageDeletionHandlerTest {

    private val testDispatcher = TestKaliumDispatcher

    @Test
    fun givenRegularMessage_whenEnqueueingForFirstTime_thenSelfDeletionShouldBeMarked() = runTest(testDispatcher.default) {
        // given
        val (arrangement, ephemeralMessageDeletionHandler) = Arrangement(this, testDispatcher)
            .withMessageRepositoryReturningMessage(
                message = TestMessage.TEXT_MESSAGE.copy(
                    expirationData = Message.ExpirationData(
                        expireAfter = 1.seconds,
                        selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                    )
                )
            )
            .withMessageRepositoryMarkingSelfDeletionStartDate()
            .arrange()

        // when
        ephemeralMessageDeletionHandler.startSelfDeletion(
            conversationId = ConversationId("someValue", "someDomain"),
            messageId = "someId"
        )

        advanceUntilIdle()

        // then
        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::markSelfDeletionStartDate)
            .with(any(), any(), any())
            .wasInvoked(exactly = Times(1))

        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::getMessageById)
            .with(any(), any())
            .wasInvoked(exactly = Times(1))
    }

    @Test
    fun givenRegularMessage_whenEnqueueingTwice_thenSelfDeletionShouldBeCalledOnce() = runTest(testDispatcher.default) {
        // given
        val (arrangement, ephemeralMessageDeletionHandler) = Arrangement(this, testDispatcher)
            .withMessageRepositoryReturningMessage(
                message = TestMessage.TEXT_MESSAGE.copy(
                    expirationData = Message.ExpirationData(
                        expireAfter = 1.seconds,
                        selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                    )
                )
            )
            .withMessageRepositoryMarkingSelfDeletionStartDate()
            .arrange()

        // when
        ephemeralMessageDeletionHandler.startSelfDeletion(
            conversationId = ConversationId("someValue", "someDomain"),
            messageId = "someId"
        )

        ephemeralMessageDeletionHandler.startSelfDeletion(
            conversationId = ConversationId("someValue", "someDomain"),
            messageId = "someId"
        )

        advanceUntilIdle()

        // then
        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::markSelfDeletionStartDate)
            .with(any(), any(), any())
            .wasInvoked(exactly = Times(1))

        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::getMessageById)
            .with(any(), any())
            .wasInvoked(exactly = Times(2))
    }

    @Test
    fun givenRegularMessageWithExpiration_whenEnqueueForDeletionAndTimeElapsed_thenTheMessageShouldBeDeleted() =
        runTest(testDispatcher.default) {
            // given
            val (arrangement, ephemeralMessageDeletionHandler) = Arrangement(this, testDispatcher)
                .withMessageRepositoryReturningMessage(
                    message = TestMessage.TEXT_MESSAGE.copy(
                        expirationData = Message.ExpirationData(
                            expireAfter = 1.seconds,
                            selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                        )
                    )
                )
                .withMessageRepositoryMarkingSelfDeletionStartDate()
                .withDeletingMessage()
                .arrange()

            // when
            ephemeralMessageDeletionHandler.startSelfDeletion(
                conversationId = ConversationId("someValue", "someDomain"),
                messageId = "someId"
            )

            advanceTimeBy(1001)

            // then
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::deleteMessage)
                .with(any(), any())
                .wasInvoked(exactly = Times(1))
        }

    @Test
    fun givenRegularMessageWihExpiration_whenEnqueueForDeletionAndTimeNotElapsed_thenTheMessageShouldNotBeDeleted() =
        runTest(testDispatcher.default) {
            // given
            val (arrangement, ephemeralMessageDeletionHandler) = Arrangement(this, testDispatcher)
                .withMessageRepositoryReturningMessage(
                    message = TestMessage.TEXT_MESSAGE.copy(
                        expirationData = Message.ExpirationData(
                            expireAfter = 1.seconds,
                            selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                        )
                    )
                )
                .withMessageRepositoryMarkingSelfDeletionStartDate()
                .withDeletingMessage()
                .arrange()

            // when
            ephemeralMessageDeletionHandler.startSelfDeletion(
                conversationId = ConversationId("someValue", "someDomain"),
                messageId = "someId"
            )

            advanceTimeBy(999)

            // then
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::deleteMessage)
                .with(any(), any())
                .wasNotInvoked()
        }

    @Test
    fun givenMultipleRegularMessageWithSameExpiration_whenEnqueuedForDeletionAndTimeElapsed_thenTheMessagesShouldBeDeleted() =
        runTest(testDispatcher.default) {
            val (arrangement, ephemeralMessageDeletionHandler) = Arrangement(this, testDispatcher)
                .withMessageRepositoryReturningMessage(
                    message = TestMessage.TEXT_MESSAGE.copy(
                        expirationData = Message.ExpirationData(
                            expireAfter = 1.seconds,
                            selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                        )
                    )
                )
                .withMessageRepositoryMarkingSelfDeletionStartDate()
                .withDeletingMessage()
                .arrange()
        }

}

private class Arrangement(private val coroutineScope: CoroutineScope, private val dispatcher: TestKaliumDispatcher) {

    @Mock
    val messageRepository = mock(classOf<MessageRepository>())

    fun withMessageRepositoryReturningMessage(
        message: Message
    ): Arrangement {
        given(messageRepository)
            .suspendFunction(messageRepository::getMessageById)
            .whenInvokedWith(any(), any())
            .then { _, _ -> Either.Right(message) }

        return this
    }

    fun withMessageRepositoryReturningSpecificMessage(
        messageId: String,
        conversatinId: ConversationId,
        message: Message
    ): Arrangement {
        given(messageRepository)
            .suspendFunction(messageRepository::getMessageById)
            .whenInvokedWith(any(), any())
            .then { _, _ -> Either.Right(message) }

        return this
    }


    fun withMessageRepositoryMarkingSelfDeletionStartDate(): Arrangement {
        given(messageRepository)
            .suspendFunction(messageRepository::markSelfDeletionStartDate)
            .whenInvokedWith(any(), any(), any())
            .then { _, _, _ -> Either.Right(Unit) }

        return this
    }

    fun withDeletingMessage(): Arrangement {
        given(messageRepository)
            .suspendFunction(messageRepository::deleteMessage)
            .whenInvokedWith(any(), any())
            .then { _, _ -> Either.Right(Unit) }

        return this
    }

    fun arrange() = this to EphemeralMessageDeletionHandlerImpl(messageRepository, dispatcher, coroutineScope)

}

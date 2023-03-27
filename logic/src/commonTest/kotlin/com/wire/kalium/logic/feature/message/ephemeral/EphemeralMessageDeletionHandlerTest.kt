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
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.oneOf
import io.mockative.twice
import io.mockative.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class EphemeralMessageDeletionHandlerTest {

    private val testDispatcher = TestKaliumDispatcher

    @Test
    fun givenRegularMessage_whenEnqueueingForFirstTime_thenSelfDeletionShouldBeMarked() = runTest(
        testDispatcher.default
    ) {
        // given
        val oneSecondEphemeralMessage = TestMessage.TEXT_MESSAGE.copy(
            id = "1",
            expirationData = Message.ExpirationData(
                expireAfter = 1.seconds,
                selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
            )
        )

        val (arrangement, ephemeralMessageDeletionHandler) = Arrangement(
            coroutineScope = this,
            dispatcher = testDispatcher
        ).withMessageRepositoryReturningMessage(oneSecondEphemeralMessage)
            .withMessageRepositoryMarkingSelfDeletionStartDate()
            .withDeletingMessage()
            .arrange()

        // when
        ephemeralMessageDeletionHandler.startSelfDeletion(
            conversationId = oneSecondEphemeralMessage.conversationId,
            messageId = oneSecondEphemeralMessage.id
        )

        advanceUntilIdle()

        // then
        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::markSelfDeletionStartDate)
            .with(eq(oneSecondEphemeralMessage.conversationId), eq(oneSecondEphemeralMessage.id), any())
            .wasInvoked(exactly = once)

        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::getMessageById)
            .with(eq(oneSecondEphemeralMessage.conversationId), eq(oneSecondEphemeralMessage.id))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRegularMessage_whenEnqueueingTwice_thenSelfDeletionShouldBeCalledOnce() = runTest(testDispatcher.default) {
        // given
        val oneSecondEphemeralMessage = TestMessage.TEXT_MESSAGE.copy(
            id = "1",
            expirationData = Message.ExpirationData(
                expireAfter = 1.seconds,
                selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
            )
        )

        val (arrangement, ephemeralMessageDeletionHandler) = Arrangement(this, testDispatcher)
            .withMessageRepositoryReturningMessage(oneSecondEphemeralMessage)
            .withMessageRepositoryMarkingSelfDeletionStartDate()
            .arrange()

        // when
        ephemeralMessageDeletionHandler.startSelfDeletion(
            conversationId = oneSecondEphemeralMessage.conversationId,
            messageId = oneSecondEphemeralMessage.id
        )

        advanceUntilIdle()

        // then

        // invoke first time
        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::markSelfDeletionStartDate)
            .with(eq(oneSecondEphemeralMessage.conversationId), eq(oneSecondEphemeralMessage.id), any())
            .wasInvoked(exactly = once)

        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::getMessageById)
            .with(eq(oneSecondEphemeralMessage.conversationId), eq(oneSecondEphemeralMessage.id))
            .wasInvoked(exactly = once)

        // invoke second time
        ephemeralMessageDeletionHandler.startSelfDeletion(
            conversationId = oneSecondEphemeralMessage.conversationId,
            messageId = oneSecondEphemeralMessage.id
        )

        advanceUntilIdle()

        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::getMessageById)
            .with(eq(oneSecondEphemeralMessage.conversationId), eq(oneSecondEphemeralMessage.id))
            .wasInvoked(exactly = once)

        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::markSelfDeletionStartDate)
            .with(eq(oneSecondEphemeralMessage.conversationId), eq(oneSecondEphemeralMessage.id), any())
            .wasNotInvoked()
    }

    @Test
    fun givenRegularMessageWithExpiration_whenEnqueueForDeletionAndTimeElapsed_thenTheMessageShouldBeDeleted() =
        runTest(testDispatcher.default) {
            // given
            val timeUntilExpiration = 1.seconds

            val oneSecondEphemeralMessage = TestMessage.TEXT_MESSAGE.copy(
                expirationData = Message.ExpirationData(
                    expireAfter = timeUntilExpiration,
                    selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                )
            )

            val (arrangement, ephemeralMessageDeletionHandler) = Arrangement(
                coroutineScope = this,
                dispatcher = testDispatcher
            ).withMessageRepositoryReturningMessage(oneSecondEphemeralMessage)
                .withMessageRepositoryMarkingSelfDeletionStartDate()
                .withDeletingMessage()
                .arrange()

            // when
            ephemeralMessageDeletionHandler.startSelfDeletion(
                conversationId = oneSecondEphemeralMessage.conversationId,
                messageId = oneSecondEphemeralMessage.id
            )

            advanceTimeBy(timeUntilExpiration + 1.milliseconds)

            // then
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::deleteMessage)
                .with(eq(oneSecondEphemeralMessage.id), eq(oneSecondEphemeralMessage.conversationId))
                .wasInvoked(exactly = once)
        }

    @Test
    fun givenRegularMessageWihExpiration_whenEnqueueForDeletionAndTimeNotElapsed_thenTheMessageShouldNotBeDeleted() =
        runTest(testDispatcher.default) {
            // given
            val timeUntilExpiration = 1.seconds

            val oneSecondEphemeralMessage = TestMessage.TEXT_MESSAGE.copy(
                expirationData = Message.ExpirationData(
                    expireAfter = timeUntilExpiration,
                    selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                )
            )

            val (arrangement, ephemeralMessageDeletionHandler) = Arrangement(this, testDispatcher)
                .withMessageRepositoryReturningMessage(oneSecondEphemeralMessage)
                .withMessageRepositoryMarkingSelfDeletionStartDate()
                .withDeletingMessage()
                .arrange()

            // when
            ephemeralMessageDeletionHandler.startSelfDeletion(
                conversationId = oneSecondEphemeralMessage.conversationId,
                messageId = oneSecondEphemeralMessage.id
            )

            advanceTimeBy(timeUntilExpiration - 1.milliseconds)

            // then
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::deleteMessage)
                .with(eq(oneSecondEphemeralMessage.conversationId), eq(oneSecondEphemeralMessage.id))
                .wasNotInvoked()
        }

    @Test
    fun givenMultipleRegularMessageWithSameExpiration_whenEnqueuedForDeletionAndTimeElapsed_thenTheMessagesShouldBeDeleted() =
        runTest(testDispatcher.default) {
            // given
            val timeUntilExpiration = 1.seconds

            val oneSecondEphemeralMessage = TestMessage.TEXT_MESSAGE.copy(
                expirationData = Message.ExpirationData(
                    expireAfter = timeUntilExpiration,
                    selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                )
            )

            val pendingMessagesToDelete = listOf(
                oneSecondEphemeralMessage.copy(
                    id = "1"
                ),
                oneSecondEphemeralMessage.copy(
                    id = "2"
                ),
                oneSecondEphemeralMessage.copy(
                    id = "3"
                ),
                oneSecondEphemeralMessage.copy(
                    id = "4"
                )
            )

            val (arrangement, ephemeralMessageDeletionHandler) = Arrangement(this, testDispatcher)
                .withMessageRepositoryReturningPendingEphemeralMessages(messages = pendingMessagesToDelete)
                .withMessageRepositoryMarkingSelfDeletionStartDate()
                .withDeletingMessage()
                .arrange()

            // when
            ephemeralMessageDeletionHandler.enqueuePendingSelfDeletionMessages()

            advanceTimeBy(timeUntilExpiration + 1.milliseconds)
            // then
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::deleteMessage)
                .with(oneOf("1", "2", "3", "4"), eq(oneSecondEphemeralMessage.conversationId))
                .wasInvoked(Times(4))
        }

    @Test
    fun givenMultipleMessageWithDifferentExpiration_whenEnqueuedForDeletionAndTimeElapsed_thenTheMessagesPastTheTimeShouldBeDeleted() =
        runTest(testDispatcher.default) {
            // given
            val oneSecondEphemeralMessage = TestMessage.TEXT_MESSAGE.copy(
                id = "1",
                expirationData = Message.ExpirationData(
                    expireAfter = 1.seconds,
                    selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                )
            )

            val twoSecondEphemeralMessage = TestMessage.TEXT_MESSAGE.copy(
                id = "2",
                expirationData = Message.ExpirationData(
                    expireAfter = 2.seconds,
                    selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                )
            )

            val threeSecondsEphemeralMessage = TestMessage.TEXT_MESSAGE.copy(
                id = "3",
                expirationData = Message.ExpirationData(
                    expireAfter = 3.seconds,
                    selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                )
            )

            val fourSecondsEphemeralMessage = TestMessage.TEXT_MESSAGE.copy(
                id = "4",
                expirationData = Message.ExpirationData(
                    expireAfter = 4.seconds,
                    selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                )
            )

            val pendingMessagesToDelete = listOf(
                oneSecondEphemeralMessage,
                twoSecondEphemeralMessage,
                threeSecondsEphemeralMessage,
                fourSecondsEphemeralMessage
            )

            val (arrangement, ephemeralMessageDeletionHandler) = Arrangement(
                coroutineScope = this,
                dispatcher = testDispatcher
            ).withMessageRepositoryReturningPendingEphemeralMessages(messages = pendingMessagesToDelete)
                .withMessageRepositoryMarkingSelfDeletionStartDate()
                .withDeletingMessage()
                .arrange()

            // when
            ephemeralMessageDeletionHandler.enqueuePendingSelfDeletionMessages()

            // then
            advanceTimeBy(1.seconds + 1.milliseconds)

            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::deleteMessage)
                .with(eq(oneSecondEphemeralMessage.id), eq(TestMessage.TEXT_MESSAGE.conversationId))
                .wasInvoked(once)

            advanceTimeBy(1.seconds + 1.milliseconds)

            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::deleteMessage)
                .with(eq(twoSecondEphemeralMessage.id), eq(TestMessage.TEXT_MESSAGE.conversationId))
                .wasInvoked(once)

            advanceTimeBy(1.seconds + 1.milliseconds)

            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::deleteMessage)
                .with(eq(threeSecondsEphemeralMessage.id), eq(TestMessage.TEXT_MESSAGE.conversationId))
                .wasInvoked(once)

            advanceTimeBy(1.seconds + 1.milliseconds)

            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::deleteMessage)
                .with(eq(fourSecondsEphemeralMessage.id), eq(TestMessage.TEXT_MESSAGE.conversationId))
                .wasInvoked(once)
        }

    @Test
    fun givenMultipleRegularMessageWithDifferentExpiration_whenEnqueuedWithTimeAdvancing_thenDeleteThosePastTheExpiration() =
        runTest(testDispatcher.default) {
            // given
            val oneSecondsEphemeralMessage = TestMessage.TEXT_MESSAGE.copy(
                expirationData = Message.ExpirationData(
                    expireAfter = 1.seconds,
                    selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                )
            )

            val twoSecondsEphemeralMessage = TestMessage.TEXT_MESSAGE.copy(
                expirationData = Message.ExpirationData(
                    expireAfter = 2.seconds,
                    selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                )
            )

            val pendingMessagesToDeletePastTheTime = listOf(
                oneSecondsEphemeralMessage.copy(id = "1"),
                oneSecondsEphemeralMessage.copy(id = "2")
            )

            val pendingMessagesToDeleteBeforeTime = listOf(
                twoSecondsEphemeralMessage.copy(id = "3"),
                twoSecondsEphemeralMessage.copy(id = "4")
            )

            val (arrangement, ephemeralMessageDeletionHandler) = Arrangement(this, testDispatcher)
                .withMessageRepositoryReturningPendingEphemeralMessages(
                    messages = pendingMessagesToDeletePastTheTime + pendingMessagesToDeleteBeforeTime
                )
                .withMessageRepositoryMarkingSelfDeletionStartDate()
                .withDeletingMessage()
                .arrange()
            // when
            ephemeralMessageDeletionHandler.enqueuePendingSelfDeletionMessages()

            advanceTimeBy(1.seconds + 500.milliseconds)
            // then
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::deleteMessage)
                .with(oneOf("1", "2"), eq(TestMessage.TEXT_MESSAGE.conversationId))
                .wasInvoked(Times(pendingMessagesToDeletePastTheTime.size))

        }

    private fun TestScope.advanceTimeBy(duration: Duration) = advanceTimeBy(duration.inWholeMilliseconds)

}

private class Arrangement(private val coroutineScope: CoroutineScope, private val dispatcher: TestKaliumDispatcher) {

    @Mock
    val messageRepository = mock(classOf<MessageRepository>())

    fun withMessageRepositoryReturningMessage(message: Message): Arrangement {
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

    fun withMessageRepositoryReturningPendingEphemeralMessages(messages: List<Message>): Arrangement {
        given(messageRepository)
            .suspendFunction(messageRepository::getEphemeralMessagesMarkedForDeletion)
            .whenInvoked()
            .then { Either.Right(messages) }

        return this
    }

    fun arrange() = this to EphemeralMessageDeletionHandlerImpl(messageRepository, dispatcher, coroutineScope)

}

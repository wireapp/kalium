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
package com.wire.kalium.logic.feature.message.ephemeral

import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.kaliumLogger
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
import io.mockative.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
            ),
            isSelfMessage = false,
            status = Message.Status.Sent
        )

        val (arrangement, ephemeralMessageDeletionHandler) = Arrangement(
            coroutineScope = this,
            dispatcher = testDispatcher
        ).withMessageRepositoryReturningMessage(oneSecondEphemeralMessage)
            .withMessageRepositoryMarkingSelfDeletionEndDate()
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
            .suspendFunction(arrangement.messageRepository::markSelfDeletionEndDate)
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
            ),
            isSelfMessage = false,
            status = Message.Status.Sent
        )

        val (arrangement, ephemeralMessageDeletionHandler) = Arrangement(this, testDispatcher)
            .withMessageRepositoryReturningMessage(oneSecondEphemeralMessage)
            .withMessageRepositoryMarkingSelfDeletionEndDate()
            .withDeletingMessage()
            .arrange()

        // when
        ephemeralMessageDeletionHandler.startSelfDeletion(
            conversationId = oneSecondEphemeralMessage.conversationId,
            messageId = oneSecondEphemeralMessage.id
        )

        // then
        advanceUntilIdle()

        // invoke first time
        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::markSelfDeletionEndDate)
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
            .suspendFunction(arrangement.messageRepository::markSelfDeletionEndDate)
            .with(eq(oneSecondEphemeralMessage.conversationId), eq(oneSecondEphemeralMessage.id), any())
            .wasNotInvoked()
    }

    @Test
    fun givenRegularMessageWithExpirationAsReceiver_whenEnqueueForDeletionAndTimeElapsed_thenTheMessageShouldBeDeleted() =
        runTest(testDispatcher.default) {
            // given
            val timeUntilExpiration = 1.seconds

            val oneSecondEphemeralMessage = TestMessage.TEXT_MESSAGE.copy(
                expirationData = Message.ExpirationData(
                    expireAfter = timeUntilExpiration,
                    selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                ),
                isSelfMessage = false,
                status = Message.Status.Sent
            )

            val (arrangement, ephemeralMessageDeletionHandler) = Arrangement(
                coroutineScope = this,
                dispatcher = testDispatcher
            ).withMessageRepositoryReturningMessage(oneSecondEphemeralMessage)
                .withMessageRepositoryMarkingSelfDeletionEndDate()
                .withDeletingMessage()
                .arrange()

            // when
            ephemeralMessageDeletionHandler.startSelfDeletion(
                conversationId = oneSecondEphemeralMessage.conversationId,
                messageId = oneSecondEphemeralMessage.id
            )

            advanceTimeBy(timeUntilExpiration + 1.milliseconds)

            // then
            verify(arrangement.deleteEphemeralMessageForSelfUserAsReceiver)
                .suspendFunction(arrangement.deleteEphemeralMessageForSelfUserAsReceiver::invoke)
                .with(eq(oneSecondEphemeralMessage.conversationId), eq(oneSecondEphemeralMessage.id))
                .wasInvoked(exactly = once)
        }

    @Test
    fun givenRegularMessageWithExpirationAsSender_whenEnqueueForDeletionAndTimeElapsed_thenTheMessageShouldBeDeleted() =
        runTest(testDispatcher.default) {
            // given
            val timeUntilExpiration = 1.seconds

            val oneSecondEphemeralMessage = TestMessage.TEXT_MESSAGE.copy(
                expirationData = Message.ExpirationData(
                    expireAfter = timeUntilExpiration,
                    selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                ),
                senderUserId = SELF_USER_ID,
                isSelfMessage = true,
                status = Message.Status.Sent
            )

            val (arrangement, ephemeralMessageDeletionHandler) = Arrangement(
                coroutineScope = this,
                dispatcher = testDispatcher
            ).withMessageRepositoryReturningMessage(oneSecondEphemeralMessage)
                .withMessageRepositoryMarkingSelfDeletionEndDate()
                .withDeletingMessage()
                .arrange()

            // when
            ephemeralMessageDeletionHandler.startSelfDeletion(
                conversationId = oneSecondEphemeralMessage.conversationId,
                messageId = oneSecondEphemeralMessage.id
            )

            advanceTimeBy(timeUntilExpiration + 1.milliseconds)

            // then
            verify(arrangement.deleteEphemeralMessageForSelfUserAsSender)
                .suspendFunction(arrangement.deleteEphemeralMessageForSelfUserAsSender::invoke)
                .with(eq(oneSecondEphemeralMessage.conversationId), eq(oneSecondEphemeralMessage.id))
                .wasInvoked(exactly = once)
        }

    @Test
    fun givenRegularMessageWihExpirationAsReceiver_whenEnqueueForDeletionAndTimeNotElapsed_thenTheMessageShouldNotBeDeleted() =
        runTest(testDispatcher.default) {
            // given
            val timeUntilExpiration = 1.seconds

            val oneSecondEphemeralMessage = TestMessage.TEXT_MESSAGE.copy(
                expirationData = Message.ExpirationData(
                    expireAfter = timeUntilExpiration,
                    selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                ),
                isSelfMessage = false,
                status = Message.Status.Sent
            )

            val (arrangement, ephemeralMessageDeletionHandler) = Arrangement(this, testDispatcher)
                .withMessageRepositoryReturningMessage(oneSecondEphemeralMessage)
                .withMessageRepositoryMarkingSelfDeletionEndDate()
                .withDeletingMessage()
                .arrange()

            // when
            ephemeralMessageDeletionHandler.startSelfDeletion(
                conversationId = oneSecondEphemeralMessage.conversationId,
                messageId = oneSecondEphemeralMessage.id
            )

            advanceTimeBy(timeUntilExpiration - 1.milliseconds)

            // then
            verify(arrangement.deleteEphemeralMessageForSelfUserAsReceiver)
                .suspendFunction(arrangement.deleteEphemeralMessageForSelfUserAsReceiver::invoke)
                .with(eq(oneSecondEphemeralMessage.id), eq(oneSecondEphemeralMessage.conversationId))
                .wasNotInvoked()
        }

    @Test
    fun givenRegularMessageWihExpirationAsSender_whenEnqueueForDeletionAndTimeNotElapsed_thenTheMessageShouldNotBeDeleted() =
        runTest(testDispatcher.default) {
            // given
            val timeUntilExpiration = 1.seconds

            val oneSecondEphemeralMessage = TestMessage.TEXT_MESSAGE.copy(
                expirationData = Message.ExpirationData(
                    expireAfter = timeUntilExpiration,
                    selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                ),
                isSelfMessage = true
            )

            val (arrangement, ephemeralMessageDeletionHandler) = Arrangement(this, testDispatcher)
                .withMessageRepositoryReturningMessage(oneSecondEphemeralMessage)
                .withMessageRepositoryMarkingSelfDeletionEndDate()
                .withDeletingMessage()
                .arrange()

            // when
            ephemeralMessageDeletionHandler.startSelfDeletion(
                conversationId = oneSecondEphemeralMessage.conversationId,
                messageId = oneSecondEphemeralMessage.id
            )

            advanceTimeBy(timeUntilExpiration - 1.milliseconds)

            // then
            verify(arrangement.deleteEphemeralMessageForSelfUserAsSender)
                .suspendFunction(arrangement.deleteEphemeralMessageForSelfUserAsSender::invoke)
                .with(eq(oneSecondEphemeralMessage.conversationId), eq(oneSecondEphemeralMessage.id))
                .wasNotInvoked()
        }

    @Test
    fun givenMultipleRegularMessageWithSameExpirationAsReceiver_whenEnqueuedForDeletionAndTimeElapsed_thenTheMessagesShouldBeDeleted() =
        runTest(testDispatcher.default) {
            // given
            val timeUntilExpiration = 1.seconds

            val oneSecondEphemeralMessage = TestMessage.TEXT_MESSAGE.copy(
                expirationData = Message.ExpirationData(
                    expireAfter = timeUntilExpiration,
                    selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                ),
                isSelfMessage = false,
                status = Message.Status.Sent
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
                .withMessageRepositoryMarkingSelfDeletionEndDate()
                .withDeletingMessage()
                .arrange()

            // when
            ephemeralMessageDeletionHandler.enqueuePendingSelfDeletionMessages()

            advanceTimeBy(timeUntilExpiration + 1.milliseconds)
            // then
            verify(arrangement.deleteEphemeralMessageForSelfUserAsReceiver)
                .suspendFunction(arrangement.deleteEphemeralMessageForSelfUserAsReceiver::invoke)
                .with(eq(oneSecondEphemeralMessage.conversationId), oneOf("1", "2", "3", "4"))
                .wasInvoked(Times(4))
        }

    @Test
    fun givenMultipleRegularMessageWithSameExpirationAsSender_whenEnqueuedForDeletionAndTimeElapsed_thenTheMessagesShouldBeDeleted() =
        runTest(testDispatcher.default) {
            // given
            val timeUntilExpiration = 1.seconds

            val oneSecondEphemeralMessage = TestMessage.TEXT_MESSAGE.copy(
                expirationData = Message.ExpirationData(
                    expireAfter = timeUntilExpiration,
                    selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                ),
                senderUserId = SELF_USER_ID,
                isSelfMessage = true,
                status = Message.Status.Sent
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
                .withMessageRepositoryMarkingSelfDeletionEndDate()
                .withDeletingMessage()
                .arrange()

            // when
            ephemeralMessageDeletionHandler.enqueuePendingSelfDeletionMessages()

            advanceTimeBy(timeUntilExpiration + 1.milliseconds)
            // then
            verify(arrangement.deleteEphemeralMessageForSelfUserAsSender)
                .suspendFunction(arrangement.deleteEphemeralMessageForSelfUserAsSender::invoke)
                .with(eq(oneSecondEphemeralMessage.conversationId), oneOf("1", "2", "3", "4"))
                .wasInvoked(Times(4))
        }

    @Test
    fun givenMultipleMessageWithDifferentExpirationAsReceiver_whenEnqueuedForDeletionAndTimeElapsed_thenTheMessagesPastTheTimeShouldBeDeleted() =
        runTest(testDispatcher.default) {
            // given
            val oneSecondEphemeralMessage = TestMessage.TEXT_MESSAGE.copy(
                id = "1",
                expirationData = Message.ExpirationData(
                    expireAfter = 1.seconds,
                    selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                ),
                isSelfMessage = false,
                status = Message.Status.Sent
            )

            val twoSecondEphemeralMessage = TestMessage.TEXT_MESSAGE.copy(
                id = "2",
                expirationData = Message.ExpirationData(
                    expireAfter = 2.seconds,
                    selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                ),
                isSelfMessage = false,
                status = Message.Status.Sent
            )

            val threeSecondsEphemeralMessage = TestMessage.TEXT_MESSAGE.copy(
                id = "3",
                expirationData = Message.ExpirationData(
                    expireAfter = 3.seconds,
                    selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                ),
                isSelfMessage = false,
                status = Message.Status.Sent
            )

            val fourSecondsEphemeralMessage = TestMessage.TEXT_MESSAGE.copy(
                id = "4",
                expirationData = Message.ExpirationData(
                    expireAfter = 4.seconds,
                    selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                ),
                isSelfMessage = false,
                status = Message.Status.Sent
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
                .withMessageRepositoryMarkingSelfDeletionEndDate()
                .withDeletingMessage()
                .arrange()

            // when
            ephemeralMessageDeletionHandler.enqueuePendingSelfDeletionMessages()

            // then
            advanceTimeBy(1.seconds + 1.milliseconds)

            verify(arrangement.deleteEphemeralMessageForSelfUserAsReceiver)
                .suspendFunction(arrangement.deleteEphemeralMessageForSelfUserAsReceiver::invoke)
                .with(eq(TestMessage.TEXT_MESSAGE.conversationId), eq(oneSecondEphemeralMessage.id))
                .wasInvoked(once)

            advanceTimeBy(1.seconds + 1.milliseconds)

            verify(arrangement.deleteEphemeralMessageForSelfUserAsReceiver)
                .suspendFunction(arrangement.deleteEphemeralMessageForSelfUserAsReceiver::invoke)
                .with(eq(TestMessage.TEXT_MESSAGE.conversationId), eq(twoSecondEphemeralMessage.id))
                .wasInvoked(once)

            advanceTimeBy(1.seconds + 1.milliseconds)

            verify(arrangement.deleteEphemeralMessageForSelfUserAsReceiver)
                .suspendFunction(arrangement.deleteEphemeralMessageForSelfUserAsReceiver::invoke)
                .with(eq(TestMessage.TEXT_MESSAGE.conversationId), (eq(threeSecondsEphemeralMessage.id)))
                .wasInvoked(once)

            advanceTimeBy(1.seconds + 1.milliseconds)

            verify(arrangement.deleteEphemeralMessageForSelfUserAsReceiver)
                .suspendFunction(arrangement.deleteEphemeralMessageForSelfUserAsReceiver::invoke)
                .with(eq(TestMessage.TEXT_MESSAGE.conversationId), eq(fourSecondsEphemeralMessage.id))
                .wasInvoked(once)
        }

    @Test
    fun givenMultipleMessageWithDifferentExpirationAsSender_whenEnqueuedForDeletionAndTimeElapsed_thenTheMessagesPastTheTimeShouldBeDeleted() =
        runTest(testDispatcher.default) {
            // given
            val oneSecondEphemeralMessage = TestMessage.TEXT_MESSAGE.copy(
                id = "1",
                expirationData = Message.ExpirationData(
                    expireAfter = 1.seconds,
                    selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                ),
                senderUserId = SELF_USER_ID,
                isSelfMessage = true,
                status = Message.Status.Sent
            )

            val twoSecondEphemeralMessage = TestMessage.TEXT_MESSAGE.copy(
                id = "2",
                expirationData = Message.ExpirationData(
                    expireAfter = 2.seconds,
                    selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                ),
                senderUserId = SELF_USER_ID,
                isSelfMessage = true,
                status = Message.Status.Sent
            )

            val threeSecondsEphemeralMessage = TestMessage.TEXT_MESSAGE.copy(
                id = "3",
                expirationData = Message.ExpirationData(
                    expireAfter = 3.seconds,
                    selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                ),
                senderUserId = SELF_USER_ID,
                isSelfMessage = true,
                status = Message.Status.Sent
            )

            val fourSecondsEphemeralMessage = TestMessage.TEXT_MESSAGE.copy(
                id = "4",
                expirationData = Message.ExpirationData(
                    expireAfter = 4.seconds,
                    selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                ),
                senderUserId = SELF_USER_ID,
                isSelfMessage = true,
                status = Message.Status.Sent
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
                .withMessageRepositoryMarkingSelfDeletionEndDate()
                .withDeletingMessage()
                .arrange()

            // when
            ephemeralMessageDeletionHandler.enqueuePendingSelfDeletionMessages()

            // then
            advanceTimeBy(1.seconds + 1.milliseconds)

            verify(arrangement.deleteEphemeralMessageForSelfUserAsSender)
                .suspendFunction(arrangement.deleteEphemeralMessageForSelfUserAsSender::invoke)
                .with(eq(TestMessage.TEXT_MESSAGE.conversationId), eq(oneSecondEphemeralMessage.id))
                .wasInvoked(once)

            advanceTimeBy(1.seconds + 1.milliseconds)

            verify(arrangement.deleteEphemeralMessageForSelfUserAsSender)
                .suspendFunction(arrangement.deleteEphemeralMessageForSelfUserAsSender::invoke)
                .with(eq(TestMessage.TEXT_MESSAGE.conversationId), eq(twoSecondEphemeralMessage.id))
                .wasInvoked(once)

            advanceTimeBy(1.seconds + 1.milliseconds)

            verify(arrangement.deleteEphemeralMessageForSelfUserAsSender)
                .suspendFunction(arrangement.deleteEphemeralMessageForSelfUserAsSender::invoke)
                .with(eq(TestMessage.TEXT_MESSAGE.conversationId), eq(threeSecondsEphemeralMessage.id))
                .wasInvoked(once)

            advanceTimeBy(1.seconds + 1.milliseconds)

            verify(arrangement.deleteEphemeralMessageForSelfUserAsSender)
                .suspendFunction(arrangement.deleteEphemeralMessageForSelfUserAsSender::invoke)
                .with(eq(TestMessage.TEXT_MESSAGE.conversationId), eq(fourSecondsEphemeralMessage.id))
                .wasInvoked(once)
        }

    @Test
    fun givenMultipleRegularMessageWithDifferentExpirationAsReceiver_whenEnqueuedWithTimeAdvancing_thenDeleteThosePastTheExpiration() =
        runTest(testDispatcher.default) {
            // given
            val oneSecondsEphemeralMessage = TestMessage.TEXT_MESSAGE.copy(
                expirationData = Message.ExpirationData(
                    expireAfter = 1.seconds,
                    selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                ),
                isSelfMessage = false,
                status = Message.Status.Sent
            )

            val twoSecondsEphemeralMessage = TestMessage.TEXT_MESSAGE.copy(
                expirationData = Message.ExpirationData(
                    expireAfter = 2.seconds,
                    selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                ),
                isSelfMessage = false,
                status = Message.Status.Sent
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
                .withMessageRepositoryMarkingSelfDeletionEndDate()
                .withDeletingMessage()
                .arrange()
            // when
            ephemeralMessageDeletionHandler.enqueuePendingSelfDeletionMessages()

            advanceTimeBy(1.seconds + 500.milliseconds)
            // then
            verify(arrangement.deleteEphemeralMessageForSelfUserAsReceiver)
                .suspendFunction(arrangement.deleteEphemeralMessageForSelfUserAsReceiver::invoke)
                .with(eq(TestMessage.TEXT_MESSAGE.conversationId), oneOf("1", "2"))
                .wasInvoked(Times(pendingMessagesToDeletePastTheTime.size))
        }

    @Test
    fun givenMultipleRegularMessageWithDifferentExpirationAsSender_whenEnqueuedWithTimeAdvancing_thenDeleteThosePastTheExpiration() =
        runTest(testDispatcher.default) {
            // given
            val oneSecondsEphemeralMessage = TestMessage.TEXT_MESSAGE.copy(
                senderUserId = SELF_USER_ID,
                expirationData = Message.ExpirationData(
                    expireAfter = 1.seconds,
                    selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                ),
                isSelfMessage = true,
                status = Message.Status.Sent
            )

            val twoSecondsEphemeralMessage = TestMessage.TEXT_MESSAGE.copy(
                expirationData = Message.ExpirationData(
                    expireAfter = 2.seconds,
                    selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                ),
                isSelfMessage = true,
                status = Message.Status.Sent
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
                .withMessageRepositoryMarkingSelfDeletionEndDate()
                .withDeletingMessage()
                .arrange()
            // when
            ephemeralMessageDeletionHandler.enqueuePendingSelfDeletionMessages()

            advanceTimeBy(1.seconds + 500.milliseconds)
            // then
            verify(arrangement.deleteEphemeralMessageForSelfUserAsSender)
                .suspendFunction(arrangement.deleteEphemeralMessageForSelfUserAsSender::invoke)
                .with(eq(TestMessage.TEXT_MESSAGE.conversationId), oneOf("1", "2"))
                .wasInvoked(Times(pendingMessagesToDeletePastTheTime.size))

        }

    @Test
    fun givenPendingMessage_whenEnqueuingMessageForSelfDelete_thenDoNothing() = runTest(
        testDispatcher.default
    ) {
        // given
        val oneSecondEphemeralMessage = TestMessage.TEXT_MESSAGE.copy(
            id = "1",
            expirationData = Message.ExpirationData(
                expireAfter = 1.seconds,
                selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
            ),
            isSelfMessage = false,
            status = Message.Status.Pending
        )

        val (arrangement, ephemeralMessageDeletionHandler) = Arrangement(
            coroutineScope = this,
            dispatcher = testDispatcher
        ).withMessageRepositoryReturningMessage(oneSecondEphemeralMessage)
            .withMessageRepositoryMarkingSelfDeletionEndDate()
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
            .suspendFunction(arrangement.messageRepository::markSelfDeletionEndDate)
            .with(eq(oneSecondEphemeralMessage.conversationId), eq(oneSecondEphemeralMessage.id), any())
            .wasNotInvoked()

        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::getMessageById)
            .with(eq(oneSecondEphemeralMessage.conversationId), eq(oneSecondEphemeralMessage.id))
            .wasInvoked(exactly = once)
    }

    private fun TestScope.advanceTimeBy(duration: Duration) = advanceTimeBy(duration.inWholeMilliseconds)


    private companion object {
        val SELF_USER_ID = UserId("self-user-id", "self-user-domain")
    }

    private class Arrangement(
        private val coroutineScope: CoroutineScope,
        private val dispatcher: TestKaliumDispatcher
    ) {

        @Mock
        val messageRepository = mock(classOf<MessageRepository>())

        @Mock
        val deleteEphemeralMessageForSelfUserAsReceiver = mock(
            classOf<DeleteEphemeralMessageForSelfUserAsReceiverUseCase>()
        )

        @Mock
        val deleteEphemeralMessageForSelfUserAsSender = mock(classOf<DeleteEphemeralMessageForSelfUserAsSenderUseCase>())

        fun withMessageRepositoryReturningMessage(message: Message): Arrangement {
            given(messageRepository)
                .suspendFunction(messageRepository::getMessageById)
                .whenInvokedWith(any(), any())
                .then { _, _ -> Either.Right(message) }

            return this
        }

        fun withMessageRepositoryMarkingSelfDeletionEndDate(): Arrangement {
            given(messageRepository)
                .suspendFunction(messageRepository::markSelfDeletionEndDate)
                .whenInvokedWith(any(), any(), any())
                .then { _, _, _ -> Either.Right(Unit) }

            return this
        }

        fun withDeletingMessage(): Arrangement {
            given(deleteEphemeralMessageForSelfUserAsReceiver)
                .suspendFunction(deleteEphemeralMessageForSelfUserAsReceiver::invoke)
                .whenInvokedWith(any(), any())
                .then { _, _ -> Either.Right(Unit) }
            given(deleteEphemeralMessageForSelfUserAsSender)
                .suspendFunction(deleteEphemeralMessageForSelfUserAsSender::invoke)
                .whenInvokedWith(any(), any())
                .then { _, _ -> Either.Right(Unit) }

            return this
        }

        fun withMessageRepositoryReturningPendingEphemeralMessages(messages: List<Message>): Arrangement {
            given(messageRepository)
                .suspendFunction(messageRepository::getAllPendingEphemeralMessages)
                .whenInvoked()
                .then { Either.Right(messages) }

            return this
        }

        fun arrange() = this to EphemeralMessageDeletionHandlerImpl(
            messageRepository,
            SELF_USER_ID,
            dispatcher,
            deleteEphemeralMessageForSelfUserAsReceiver,
            deleteEphemeralMessageForSelfUserAsSender,
            kaliumLogger,
            coroutineScope,
        )
    }

}

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

import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.MessageOperationResult
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matching
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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

        ephemeralMessageDeletionHandler.startSelfDeletion(
            conversationId = oneSecondEphemeralMessage.conversationId,
            messageId = oneSecondEphemeralMessage.id
        )

        advanceUntilIdle()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.markSelfDeletionEndDate(
                eq(oneSecondEphemeralMessage.conversationId),
                eq(oneSecondEphemeralMessage.id),
                any()
            )
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.getMessageById(eq(oneSecondEphemeralMessage.conversationId), eq(oneSecondEphemeralMessage.id))
        }
    }

    @Test
    fun givenRegularMessage_whenEnqueueingTwice_thenSelfDeletionShouldBeCalledOnce() = runTest(testDispatcher.default) {
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

        ephemeralMessageDeletionHandler.startSelfDeletion(
            conversationId = oneSecondEphemeralMessage.conversationId,
            messageId = oneSecondEphemeralMessage.id
        )

        advanceUntilIdle()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.markSelfDeletionEndDate(
                eq(oneSecondEphemeralMessage.conversationId),
                eq(oneSecondEphemeralMessage.id),
                any()
            )
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.getMessageById(eq(oneSecondEphemeralMessage.conversationId), eq(oneSecondEphemeralMessage.id))
        }

        ephemeralMessageDeletionHandler.startSelfDeletion(
            conversationId = oneSecondEphemeralMessage.conversationId,
            messageId = oneSecondEphemeralMessage.id
        )

        advanceUntilIdle()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.getMessageById(eq(oneSecondEphemeralMessage.conversationId), eq(oneSecondEphemeralMessage.id))
        }

        verifySuspend(VerifyMode.not) {
            arrangement.messageRepository.markSelfDeletionEndDate(
                eq(oneSecondEphemeralMessage.conversationId),
                eq(oneSecondEphemeralMessage.id),
                any()
            )
        }
    }

    @Test
    fun givenRegularMessageWithExpirationAsReceiver_whenEnqueueForDeletionAndTimeElapsed_thenTheMessageShouldBeDeleted() =
        runTest(testDispatcher.default) {
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

            ephemeralMessageDeletionHandler.startSelfDeletion(
                conversationId = oneSecondEphemeralMessage.conversationId,
                messageId = oneSecondEphemeralMessage.id
            )

            advanceTimeBy(timeUntilExpiration + 1.milliseconds)

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.deleteEphemeralMessageForSelfUserAsReceiver.invoke(
                    eq(oneSecondEphemeralMessage.conversationId),
                    eq(oneSecondEphemeralMessage.id)
                )
            }
        }

    @Test
    fun givenRegularMessageWithExpirationAsSender_whenEnqueueForDeletionAndTimeElapsed_thenTheMessageShouldBeDeleted() =
        runTest(testDispatcher.default) {
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

            ephemeralMessageDeletionHandler.startSelfDeletion(
                conversationId = oneSecondEphemeralMessage.conversationId,
                messageId = oneSecondEphemeralMessage.id
            )

            advanceTimeBy(timeUntilExpiration + 1.milliseconds)

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.deleteEphemeralMessageForSelfUserAsSender.invoke(
                    eq(oneSecondEphemeralMessage.conversationId),
                    eq(oneSecondEphemeralMessage.id)
                )
            }
        }

    @Test
    fun givenRegularMessageWihExpirationAsReceiver_whenEnqueueForDeletionAndTimeNotElapsed_thenTheMessageShouldNotBeDeleted() =
        runTest(testDispatcher.default) {
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

            ephemeralMessageDeletionHandler.startSelfDeletion(
                conversationId = oneSecondEphemeralMessage.conversationId,
                messageId = oneSecondEphemeralMessage.id
            )

            advanceTimeBy(timeUntilExpiration - 1.milliseconds)

            verifySuspend(VerifyMode.not) {
                arrangement.deleteEphemeralMessageForSelfUserAsReceiver.invoke(
                    eq(oneSecondEphemeralMessage.conversationId),
                    eq(oneSecondEphemeralMessage.id),
                )
            }
        }

    @Test
    fun givenRegularMessageWihExpirationAsSender_whenEnqueueForDeletionAndTimeNotElapsed_thenTheMessageShouldNotBeDeleted() =
        runTest(testDispatcher.default) {
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

            ephemeralMessageDeletionHandler.startSelfDeletion(
                conversationId = oneSecondEphemeralMessage.conversationId,
                messageId = oneSecondEphemeralMessage.id
            )

            advanceTimeBy(timeUntilExpiration - 1.milliseconds)

            verifySuspend(VerifyMode.not) {
                arrangement.deleteEphemeralMessageForSelfUserAsSender.invoke(
                    eq(oneSecondEphemeralMessage.conversationId),
                    eq(oneSecondEphemeralMessage.id)
                )
            }
        }

    @Test
    fun givenMultipleRegularMessageWithSameExpirationAsReceiver_whenEnqueuedForDeletionAndTimeElapsed_thenTheMessagesShouldBeDeleted() =
        runTest(testDispatcher.default) {
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

            ephemeralMessageDeletionHandler.enqueuePendingSelfDeletionMessages()

            advanceTimeBy(timeUntilExpiration + 1.milliseconds)
            verifySuspend(VerifyMode.exactly(4)) {
                arrangement.deleteEphemeralMessageForSelfUserAsReceiver.invoke(
                    eq(oneSecondEphemeralMessage.conversationId),
                    matching { it in listOf("1", "2", "3", "4") }
                )
            }
        }

    @Test
    fun givenMultipleRegularMessageWithSameExpirationAsSender_whenEnqueuedForDeletionAndTimeElapsed_thenTheMessagesShouldBeDeleted() =
        runTest(testDispatcher.default) {
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

            ephemeralMessageDeletionHandler.enqueuePendingSelfDeletionMessages()

            advanceTimeBy(timeUntilExpiration + 1.milliseconds)
            verifySuspend(VerifyMode.exactly(4)) {
                arrangement.deleteEphemeralMessageForSelfUserAsSender.invoke(
                    eq(oneSecondEphemeralMessage.conversationId),
                    matching { it in listOf("1", "2", "3", "4") }
                )
            }
        }

    @Test
    fun givenMultipleMessageWithDifferentExpirationAsReceiver_whenEnqueuedForDeletionAndTimeElapsed_thenTheMessagesPastTheTimeShouldBeDeleted() =
        runTest(testDispatcher.default) {
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

            ephemeralMessageDeletionHandler.enqueuePendingSelfDeletionMessages()

            advanceTimeBy(1.seconds + 1.milliseconds)

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.deleteEphemeralMessageForSelfUserAsReceiver.invoke(
                    eq(TestMessage.TEXT_MESSAGE.conversationId),
                    eq(oneSecondEphemeralMessage.id)
                )
            }

            advanceTimeBy(1.seconds + 1.milliseconds)

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.deleteEphemeralMessageForSelfUserAsReceiver.invoke(
                    eq(TestMessage.TEXT_MESSAGE.conversationId),
                    eq(twoSecondEphemeralMessage.id)
                )
            }

            advanceTimeBy(1.seconds + 1.milliseconds)

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.deleteEphemeralMessageForSelfUserAsReceiver.invoke(
                    eq(TestMessage.TEXT_MESSAGE.conversationId),
                    (eq(threeSecondsEphemeralMessage.id))
                )
            }

            advanceTimeBy(1.seconds + 1.milliseconds)

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.deleteEphemeralMessageForSelfUserAsReceiver.invoke(
                    eq(TestMessage.TEXT_MESSAGE.conversationId),
                    eq(fourSecondsEphemeralMessage.id)
                )
            }
        }

    @Test
    fun givenMultipleMessageWithDifferentExpirationAsSender_whenEnqueuedForDeletionAndTimeElapsed_thenTheMessagesPastTheTimeShouldBeDeleted() =
        runTest(testDispatcher.default) {
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

            ephemeralMessageDeletionHandler.enqueuePendingSelfDeletionMessages()

            advanceTimeBy(1.seconds + 1.milliseconds)

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.deleteEphemeralMessageForSelfUserAsSender.invoke(
                    eq(TestMessage.TEXT_MESSAGE.conversationId),
                    eq(oneSecondEphemeralMessage.id)
                )
            }

            advanceTimeBy(1.seconds + 1.milliseconds)

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.deleteEphemeralMessageForSelfUserAsSender.invoke(
                    eq(TestMessage.TEXT_MESSAGE.conversationId),
                    eq(twoSecondEphemeralMessage.id)
                )
            }

            advanceTimeBy(1.seconds + 1.milliseconds)

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.deleteEphemeralMessageForSelfUserAsSender.invoke(
                    eq(TestMessage.TEXT_MESSAGE.conversationId),
                    eq(threeSecondsEphemeralMessage.id)
                )
            }

            advanceTimeBy(1.seconds + 1.milliseconds)

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.deleteEphemeralMessageForSelfUserAsSender.invoke(
                    eq(TestMessage.TEXT_MESSAGE.conversationId),
                    eq(fourSecondsEphemeralMessage.id)
                )
            }
        }

    @Test
    fun givenMultipleRegularMessageWithDifferentExpirationAsReceiver_whenEnqueuedWithTimeAdvancing_thenDeleteThosePastTheExpiration() =
        runTest(testDispatcher.default) {
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
            ephemeralMessageDeletionHandler.enqueuePendingSelfDeletionMessages()

            advanceTimeBy(1.seconds + 500.milliseconds)
            verifySuspend(VerifyMode.exactly(pendingMessagesToDeletePastTheTime.size)) {
                arrangement.deleteEphemeralMessageForSelfUserAsReceiver.invoke(eq(TestMessage.TEXT_MESSAGE.conversationId), matching { it in listOf("1", "2") })
            }
        }

    @Test
    fun givenMultipleRegularMessageWithDifferentExpirationAsSender_whenEnqueuedWithTimeAdvancing_thenDeleteThosePastTheExpiration() =
        runTest(testDispatcher.default) {
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
            ephemeralMessageDeletionHandler.enqueuePendingSelfDeletionMessages()

            advanceTimeBy(1.seconds + 500.milliseconds)
            verifySuspend(VerifyMode.exactly(pendingMessagesToDeletePastTheTime.size)) {
                arrangement.deleteEphemeralMessageForSelfUserAsSender.invoke(eq(TestMessage.TEXT_MESSAGE.conversationId), matching { it in listOf("1", "2") })
            }

        }

    @Test
    fun givenPendingMessage_whenEnqueuingMessageForSelfDelete_thenDoNothing() = runTest(
        testDispatcher.default
    ) {
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

        ephemeralMessageDeletionHandler.startSelfDeletion(
            conversationId = oneSecondEphemeralMessage.conversationId,
            messageId = oneSecondEphemeralMessage.id
        )

        advanceUntilIdle()

        verifySuspend(VerifyMode.not) {
            arrangement.messageRepository.markSelfDeletionEndDate(
                eq(oneSecondEphemeralMessage.conversationId),
                eq(oneSecondEphemeralMessage.id),
                any()
            )
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.getMessageById(eq(oneSecondEphemeralMessage.conversationId), eq(oneSecondEphemeralMessage.id))
        }
    }

    private fun TestScope.advanceTimeBy(duration: Duration) = advanceTimeBy(duration.inWholeMilliseconds)


    private companion object {
        val SELF_USER_ID = UserId("self-user-id", "self-user-domain")
    }

    private class Arrangement(
        private val coroutineScope: CoroutineScope,
        private val dispatcher: TestKaliumDispatcher
    ) {

        val messageRepository = mock<MessageRepository>(mode = MockMode.autoUnit)
        val deleteEphemeralMessageForSelfUserAsReceiver = mock<DeleteEphemeralMessageForSelfUserAsReceiverUseCase>(mode = MockMode.autoUnit)
        val deleteEphemeralMessageForSelfUserAsSender = mock<DeleteEphemeralMessageForSelfUserAsSenderUseCase>(mode = MockMode.autoUnit)

        suspend fun withMessageRepositoryReturningMessage(message: Message): Arrangement {
            everySuspend {
                messageRepository.getMessageById(any(), any())
            } returns Either.Right(message)

            return this
        }

        suspend fun withMessageRepositoryMarkingSelfDeletionEndDate(): Arrangement {
            everySuspend {
                messageRepository.markSelfDeletionEndDate(any(), any(), any())
            } returns Either.Right(Unit)

            return this
        }

        suspend fun withDeletingMessage(): Arrangement {
            everySuspend {
                deleteEphemeralMessageForSelfUserAsReceiver.invoke(any(), any())
            } returns MessageOperationResult.Success
            everySuspend {
                deleteEphemeralMessageForSelfUserAsSender.invoke(any(), any())
            } returns MessageOperationResult.Success

            return this
        }

        suspend fun withMessageRepositoryReturningPendingEphemeralMessages(messages: List<Message>): Arrangement {
            everySuspend {
                messageRepository.getAllPendingEphemeralMessages()
            } returns Either.Right(messages)

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

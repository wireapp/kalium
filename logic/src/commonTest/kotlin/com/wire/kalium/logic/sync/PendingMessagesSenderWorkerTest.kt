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

package com.wire.kalium.logic.sync

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.feature.message.MessageSendFailureHandler
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.messaging.sending.MessageSender
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matching
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test

class PendingMessagesSenderWorkerTest {

    private val messageRepository = mock<MessageRepository>()
    private val messageSender = mock<MessageSender>()
    private val sendPendingAssetMessage = mock<SendPendingAssetMessageUseCase>(mode = MockMode.autoUnit)
    private val messageSendFailureHandler = mock<MessageSendFailureHandler>(mode = MockMode.autoUnit)

    private lateinit var pendingMessagesSenderWorker: PendingMessagesSenderWorker

    @BeforeTest
    fun setup() {
        pendingMessagesSenderWorker = PendingMessagesSenderWorker(
            messageRepository,
            messageSender,
            TestUser.USER_ID,
            sendPendingAssetMessage,
            messageSendFailureHandler,
        )
    }

    @Test
    fun givenPendingTextMessage_whenExecutingAWorker_thenSendPendingMessageIsCalled() = runTest {
        val message = TestMessage.TEXT_MESSAGE
        everySuspend {
            messageRepository.getAllPendingMessagesFromUser(eq(TestUser.USER_ID))
        } returns Either.Right(listOf(message))
        everySuspend {
            messageSender.sendPendingMessage(eq(message.conversationId), eq(message.id))
        } returns Either.Right(Unit)

        pendingMessagesSenderWorker.doWork()

        verifySuspend(VerifyMode.exactly(1)) {
            messageSender.sendPendingMessage(eq(message.conversationId), eq(message.id))
        }
        verifySuspend(VerifyMode.not) {
            sendPendingAssetMessage.invoke(any<Message.Regular>())
        }
        verifySuspend(VerifyMode.not) {
            messageSender.sendMessage(any(), any())
        }
    }

    @Test
    fun givenPendingAssetMessage_whenExecutingAWorker_thenSendPendingAssetMessageIsCalled() = runTest {
        val message = TestMessage.assetMessage()
        everySuspend {
            messageRepository.getAllPendingMessagesFromUser(eq(TestUser.USER_ID))
        } returns Either.Right(listOf(message))
        everySuspend {
            sendPendingAssetMessage.invoke(eq(message))
        } returns Either.Right(Unit)

        pendingMessagesSenderWorker.doWork()

        verifySuspend(VerifyMode.exactly(1)) {
            sendPendingAssetMessage.invoke(eq(message))
        }
        verifySuspend(VerifyMode.not) {
            messageSender.sendPendingMessage(any(), any())
        }
    }

    @Test
    fun givenPendingMessagesReturnsFailure_whenExecutingAWorker_thenDoNothing() = runTest {
        val dataNotFoundFailure = StorageFailure.DataNotFound
        everySuspend {
            messageRepository.getAllPendingMessagesFromUser(eq(TestUser.USER_ID))
        } returns Either.Left(dataNotFoundFailure)

        pendingMessagesSenderWorker.doWork()

        verifySuspend(VerifyMode.not) {
            messageSender.sendPendingMessage(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            sendPendingAssetMessage.invoke(any<Message.Regular>())
        }
    }

    @Test
    fun givenPendingTextMessageWithEditedStatus_whenExecutingWorker_thenTextEditedSignalingIsSent() = runTest {
        val editedMessage = TestMessage.TEXT_MESSAGE.copy(
            editStatus = Message.EditStatus.Edited(Instant.fromEpochMilliseconds(123L))
        )
        everySuspend {
            messageRepository.getAllPendingMessagesFromUser(eq(TestUser.USER_ID))
        } returns Either.Right(listOf(editedMessage))
        everySuspend {
            messageSender.sendMessage(any(), any())
        } returns Either.Right(Unit)

        pendingMessagesSenderWorker.doWork()

        verifySuspend(VerifyMode.exactly(1)) {
            messageSender.sendMessage(
                matching { signaling ->
                    signaling is Message.Signaling &&
                            signaling.content is MessageContent.TextEdited &&
                            (signaling.content as MessageContent.TextEdited).editMessageId == editedMessage.id &&
                            (signaling.content as MessageContent.TextEdited).newContent ==
                            (editedMessage.content as MessageContent.Text).value
                },
                any()
            )
        }
        verifySuspend(VerifyMode.not) {
            messageSender.sendPendingMessage(any(), any())
        }
    }

    @Test
    fun givenPendingEditedMessageFailsWithNoNetwork_whenExecutingWorker_thenFailureHandlerCalledWithoutReschedule() = runTest {
        val editedMessage = TestMessage.TEXT_MESSAGE.copy(
            editStatus = Message.EditStatus.Edited(Instant.fromEpochMilliseconds(123L))
        )
        val failure = NetworkFailure.NoNetworkConnection(null)
        everySuspend {
            messageRepository.getAllPendingMessagesFromUser(eq(TestUser.USER_ID))
        } returns Either.Right(listOf(editedMessage))
        everySuspend {
            messageSender.sendMessage(any(), any())
        } returns Either.Left(failure)

        pendingMessagesSenderWorker.doWork()

        verifySuspend(VerifyMode.exactly(1)) {
            messageSendFailureHandler.handleFailureAndUpdateMessageStatus(
                failure = eq(failure),
                conversationId = eq(editedMessage.conversationId),
                messageId = eq(editedMessage.id),
                messageType = eq("TextEdited"),
                scheduleResendIfNoNetwork = eq(false),
            )
        }
    }
}

/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.feature.message.MessageSendFailureHandler
import com.wire.kalium.logic.framework.TestMessage.TEST_DATE
import com.wire.kalium.logic.framework.TestMessage.TEXT_CONTENT
import com.wire.kalium.logic.framework.TestMessage.TEXT_MESSAGE
import com.wire.kalium.logic.framework.TestMessage.assetMessage
import com.wire.kalium.logic.framework.TestUser.USER_ID
import com.wire.kalium.messaging.sending.MessageSender
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.matching
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class SendPendingMessagesUseCaseTest {

    @Test
    fun givenNoPendingMessages_whenInvoked_thenReturnsSuccess() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withPendingMessages(Either.Right(emptyList()))
            .arrange()

        val result = useCase()

        assertIs<SendPendingMessagesUseCase.Result.Success>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.getAllPendingMessagesFromUser(USER_ID)
        }
    }

    @Test
    fun givenFetchingPendingMessagesFails_whenInvoked_thenReturnsFailureWithoutSendingMessages() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withPendingMessages(Either.Left(StorageFailure.Generic(RuntimeException("failure"))))
            .arrange()

        val result = useCase()

        assertIs<SendPendingMessagesUseCase.Result.Failure>(result)
        verifySuspend(VerifyMode.not) {
            arrangement.sendPendingAssetMessage(any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.messageSender.sendPendingMessage(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.messageSender.sendMessage(any(), any())
        }
    }

    @Test
    fun givenPendingAssetMessage_whenInvoked_thenSendsItWithAssetUseCase() = runTest {
        val message = assetMessage()
        val (arrangement, useCase) = Arrangement()
            .withPendingMessages(Either.Right(listOf(message)))
            .withPendingAssetMessageResult(Either.Right(Unit))
            .arrange()

        val result = useCase()

        assertIs<SendPendingMessagesUseCase.Result.Success>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.sendPendingAssetMessage(message)
        }
        verifySuspend(VerifyMode.not) {
            arrangement.messageSender.sendPendingMessage(any(), any())
        }
    }

    @Test
    fun givenPendingUneditedTextMessage_whenInvoked_thenSendsItAsPendingMessage() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withPendingMessages(Either.Right(listOf(TEXT_MESSAGE)))
            .withPendingMessageResult(Either.Right(Unit))
            .arrange()

        val result = useCase()

        assertIs<SendPendingMessagesUseCase.Result.Success>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendPendingMessage(TEXT_MESSAGE.conversationId, TEXT_MESSAGE.id)
        }
        verifySuspend(VerifyMode.not) {
            arrangement.messageSender.sendMessage(any(), any())
        }
    }

    @Test
    fun givenPendingEditedTextMessage_whenInvoked_thenSendsItAsTextEditSignalingMessage() = runTest {
        val message = TEXT_MESSAGE.copy(editStatus = Message.EditStatus.Edited(TEST_DATE))
        val (arrangement, useCase) = Arrangement()
            .withPendingMessages(Either.Right(listOf(message)))
            .withSendMessageResult(Either.Right(Unit))
            .arrange()

        val result = useCase()

        assertIs<SendPendingMessagesUseCase.Result.Success>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(
                matching {
                    val content = it.content as? MessageContent.TextEdited
                    it is Message.Signaling &&
                            it.id != message.id &&
                            it.conversationId == message.conversationId &&
                            content?.editMessageId == message.id &&
                            content.newContent == TEXT_CONTENT.value
                },
                any()
            )
        }
        verifySuspend(VerifyMode.not) {
            arrangement.messageSender.sendPendingMessage(any(), any())
        }
    }

    @Test
    fun givenSendingPendingEditedTextMessageFails_whenInvoked_thenHandlesFailureWithoutSchedulingResend() = runTest {
        val message = TEXT_MESSAGE.copy(editStatus = Message.EditStatus.Edited(TEST_DATE))
        val failure = NetworkFailure.NoNetworkConnection(null)
        val (arrangement, useCase) = Arrangement()
            .withPendingMessages(Either.Right(listOf(message)))
            .withSendMessageResult(Either.Left(failure))
            .arrange()

        val result = useCase()

        assertIs<SendPendingMessagesUseCase.Result.Success>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(
                failure = failure,
                conversationId = message.conversationId,
                messageId = message.id,
                messageType = "TextEdited",
                scheduleResendIfNoNetwork = false
            )
        }
    }

    private class Arrangement {
        val messageRepository = mock<MessageRepository>(mode = MockMode.autoUnit)
        val messageSender = mock<MessageSender>(mode = MockMode.autoUnit)
        val sendPendingAssetMessage = mock<SendPendingAssetMessageUseCase>(mode = MockMode.autoUnit)
        val messageSendFailureHandler = mock<MessageSendFailureHandler>(mode = MockMode.autoUnit)

        suspend fun withPendingMessages(result: Either<CoreFailure, List<Message>>) = apply {
            everySuspend { messageRepository.getAllPendingMessagesFromUser(USER_ID) } returns result
        }

        suspend fun withPendingAssetMessageResult(result: Either<CoreFailure, Unit>) = apply {
            everySuspend { sendPendingAssetMessage(any()) } returns result
        }

        suspend fun withPendingMessageResult(result: Either<CoreFailure, Unit>) = apply {
            everySuspend { messageSender.sendPendingMessage(any(), any()) } returns result
        }

        suspend fun withSendMessageResult(result: Either<CoreFailure, Unit>) = apply {
            everySuspend { messageSender.sendMessage(any(), any()) } returns result
        }

        fun arrange() = this to SendPendingMessagesUseCaseImpl(
            messageRepository = messageRepository,
            messageSender = messageSender,
            userId = USER_ID,
            sendPendingAssetMessage = sendPendingAssetMessage,
            messageSendFailureHandler = messageSendFailureHandler,
        )
    }
}

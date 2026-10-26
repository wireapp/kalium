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
import com.wire.kalium.logic.framework.TestMessage.MISSED_CALL_MESSAGE
import com.wire.kalium.logic.framework.TestMessage.TEST_DATE
import com.wire.kalium.logic.framework.TestMessage.TEXT_CONTENT
import com.wire.kalium.logic.framework.TestMessage.TEXT_MESSAGE
import com.wire.kalium.logic.framework.TestMessage.assetMessage
import com.wire.kalium.logic.framework.TestMessage.multipartMessage
import com.wire.kalium.logic.framework.TestUser.USER_ID
import com.wire.kalium.messaging.sending.MessageSendFailureHandler
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.persistence.dao.message.MessageEntity
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

class SendPendingMessagesUseCaseTest {

    @Test
    fun givenMessageJustUnderSevenDaysOld_whenInvoked_thenSendsIt() = runTest {
        val message = TEXT_MESSAGE.copy(date = TEST_DATE - 7.days + 1.milliseconds)
        val (arrangement, useCase) = Arrangement()
            .withPendingMessages(Either.Right(listOf(message)))
            .withPendingMessageResult(Either.Right(Unit))
            .arrange()

        assertIs<SendPendingMessagesUseCase.Result.Success>(useCase())

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendPendingMessage(message.conversationId, message.id)
        }
        verifySuspend(VerifyMode.not) {
            arrangement.messageRepository.updateMessageStatus(any(), any(), any())
        }
    }

    @Test
    fun givenMessageExactlySevenDaysOld_whenInvoked_thenFailsWithoutSending() =
        assertExpiredWithoutSending(TEXT_MESSAGE.copy(date = TEST_DATE - 7.days))

    @Test
    fun givenMessageOverSevenDaysOld_whenInvoked_thenFailsWithoutSending() =
        assertExpiredWithoutSending(TEXT_MESSAGE.copy(date = TEST_DATE - 7.days - 1.milliseconds))

    @Test
    fun givenVeryOldPendingMessage_whenFirstInvoked_thenFailsWithoutSending() =
        assertExpiredWithoutSending(TEXT_MESSAGE.copy(date = TEST_DATE - 365.days))

    @Test
    fun givenExpiredAsset_whenInvoked_thenFailsWithoutUploadingOrSending() =
        assertExpiredWithoutSending(assetMessage().copy(date = TEST_DATE - 7.days))

    @Test
    fun givenEditExactlySevenDaysOld_whenInvoked_thenFailsWithoutSendingSignaling() =
        assertExpiredWithoutSending(
            TEXT_MESSAGE.copy(date = TEST_DATE - 365.days, editStatus = Message.EditStatus.Edited(TEST_DATE - 7.days))
        )

    @Test
    fun givenOlderEdit_whenInvoked_thenFailsWithoutSendingSignaling() =
        assertExpiredWithoutSending(
            TEXT_MESSAGE.copy(date = TEST_DATE - 365.days, editStatus = Message.EditStatus.Edited(TEST_DATE - 8.days))
        )

    @Test
    fun givenRecentEditOfOldMessage_whenInvoked_thenSendsEdit() = runTest {
        val message = TEXT_MESSAGE.copy(
            date = TEST_DATE - 365.days,
            editStatus = Message.EditStatus.Edited(TEST_DATE - 7.days + 1.milliseconds)
        )
        val (arrangement, useCase) = Arrangement()
            .withPendingMessages(Either.Right(listOf(message)))
            .withSendMessageResult(Either.Right(Unit))
            .arrange()

        assertIs<SendPendingMessagesUseCase.Result.Success>(useCase())

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(
                matches { (it.content as? MessageContent.TextEdited)?.editMessageId == message.id },
                any()
            )
        }
        verifySuspend(VerifyMode.not) {
            arrangement.messageRepository.updateMessageStatus(any(), any(), any())
        }
    }

    @Test
    fun givenMixedBacklog_whenInvoked_thenFailsExpiredMessagesAndSendsEligibleOnes() = runTest {
        val expired = TEXT_MESSAGE.copy(id = "expired", date = TEST_DATE - 7.days)
        val (arrangement, useCase) = Arrangement()
            .withPendingMessages(Either.Right(listOf(expired, TEXT_MESSAGE)))
            .withUpdateMessageStatusResult(Either.Right(Unit))
            .withPendingMessageResult(Either.Right(Unit))
            .arrange()

        assertIs<SendPendingMessagesUseCase.Result.Success>(useCase())

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.updateMessageStatus(MessageEntity.Status.FAILED, expired.conversationId, expired.id)
            arrangement.messageSender.sendPendingMessage(TEXT_MESSAGE.conversationId, TEXT_MESSAGE.id)
        }
        verifySuspend(VerifyMode.not) {
            arrangement.messageSender.sendPendingMessage(expired.conversationId, expired.id)
        }
    }

    @Test
    fun givenExpiryUpdateFails_whenInvoked_thenSkipsExpiredMessageAndContinuesWithFailureResult() = runTest {
        val expired = TEXT_MESSAGE.copy(id = "expired", date = TEST_DATE - 7.days)
        val (arrangement, useCase) = Arrangement()
            .withPendingMessages(Either.Right(listOf(expired, TEXT_MESSAGE)))
            .withUpdateMessageStatusResult(Either.Left(StorageFailure.Generic(RuntimeException("failure"))))
            .withPendingMessageResult(Either.Right(Unit))
            .arrange()

        assertIs<SendPendingMessagesUseCase.Result.Failure>(useCase())

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.updateMessageStatus(MessageEntity.Status.FAILED, expired.conversationId, expired.id)
            arrangement.messageSender.sendPendingMessage(TEXT_MESSAGE.conversationId, TEXT_MESSAGE.id)
        }
        verifySuspend(VerifyMode.not) {
            arrangement.messageSender.sendPendingMessage(expired.conversationId, expired.id)
            arrangement.messageSender.sendMessage(any(), any())
            arrangement.sendPendingAssetMessage(any())
        }
    }

    @Test
    fun givenOldNonRegularMessage_whenInvoked_thenLeavesItUnchanged() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withPendingMessages(Either.Right(listOf(MISSED_CALL_MESSAGE.copy(date = TEST_DATE - 365.days))))
            .arrange()

        assertIs<SendPendingMessagesUseCase.Result.Success>(useCase())

        verifySuspend(VerifyMode.not) {
            arrangement.messageRepository.updateMessageStatus(any(), any(), any())
            arrangement.messageSender.sendPendingMessage(any(), any())
            arrangement.messageSender.sendMessage(any(), any())
            arrangement.sendPendingAssetMessage(any())
        }
    }

    private fun assertExpiredWithoutSending(message: Message.Regular) = runTest {
        val (arrangement, useCase) = Arrangement()
            .withPendingMessages(Either.Right(listOf(message)))
            .withUpdateMessageStatusResult(Either.Right(Unit))
            .arrange()

        assertIs<SendPendingMessagesUseCase.Result.Success>(useCase())

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.updateMessageStatus(MessageEntity.Status.FAILED, message.conversationId, message.id)
        }
        verifySuspend(VerifyMode.not) {
            arrangement.messageSender.sendPendingMessage(any(), any())
            arrangement.messageSender.sendMessage(any(), any())
            arrangement.sendPendingAssetMessage(any())
        }
    }

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
    fun givenPendingLocationMessage_whenInvoked_thenSendsItAsPendingMessage() = runTest {
        val message = TEXT_MESSAGE.copy(content = MessageContent.Location(1F, 2F))
        val (arrangement, useCase) = Arrangement()
            .withPendingMessages(Either.Right(listOf(message)))
            .withPendingMessageResult(Either.Right(Unit))
            .arrange()

        val result = useCase()

        assertIs<SendPendingMessagesUseCase.Result.Success>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendPendingMessage(message.conversationId, message.id)
        }
    }

    @Test
    fun givenPendingMultipartMessage_whenInvoked_thenSendsItAsPendingMessage() = runTest {
        val message = multipartMessage()
        val (arrangement, useCase) = Arrangement()
            .withPendingMessages(Either.Right(listOf(message)))
            .withPendingMessageResult(Either.Right(Unit))
            .arrange()

        val result = useCase()

        assertIs<SendPendingMessagesUseCase.Result.Success>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendPendingMessage(message.conversationId, message.id)
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
                matches {
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

        suspend fun withUpdateMessageStatusResult(result: Either<CoreFailure, Unit>) = apply {
            everySuspend { messageRepository.updateMessageStatus(any(), any(), any()) } returns result
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
            clock = object : Clock {
                override fun now() = TEST_DATE
            },
        )
    }
}

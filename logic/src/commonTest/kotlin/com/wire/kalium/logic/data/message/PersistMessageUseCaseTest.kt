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
package com.wire.kalium.logic.data.message

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.persistence.dao.message.InsertMessageResult
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class PersistMessageUseCaseTest {

    @Test
    fun givenMessageRepositoryFailure_whenPersistingMessage_thenReturnFailure() = runTest {
        val (arrangement, persistMessage) = Arrangement()
            .withPersistMessageFailure()
            .withReceiptMode()
            .arrange()
        val message = TestMessage.TEXT_MESSAGE

        val result = persistMessage.invoke(message)

        result.shouldFail()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.persistMessage(any(), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.getReceiptModeFromGroupConversationByQualifiedID(any())
        }
    }

    @Test
    fun givenAMessageAndMessageRepositorySuccess_whenPersistingMessage_thenScheduleRegularNotificationChecking() =
        runTest {
            val (arrangement, persistMessage) = Arrangement()
                .withPersistMessageSuccess()
                .withReceiptMode()
                .arrange()
            val message = TestMessage.TEXT_MESSAGE.copy(
                senderUserId = UserId("id", "domain"),
            )

            val result = persistMessage.invoke(message)

            result.shouldSucceed()

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.persistMessage(any(), any())
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.getReceiptModeFromGroupConversationByQualifiedID(any())
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.notificationEventsManager.scheduleRegularNotificationChecking()
            }
        }

    @Test
    fun givenSelfMessageAndMessageRepositorySuccess_whenPersistingMessage_thenDoNotScheduleRegularNotificationChecking() =
        runTest {
            val (arrangement, persistMessage) = Arrangement()
                .withPersistMessageSuccess()
                .withReceiptMode()
                .arrange()
            val message = TestMessage.TEXT_MESSAGE

            val result = persistMessage.invoke(message)

            result.shouldSucceed()

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.persistMessage(any(), any())
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.getReceiptModeFromGroupConversationByQualifiedID(any())
            }

            verifySuspend(VerifyMode.not) {
                arrangement.notificationEventsManager.scheduleRegularNotificationChecking()
            }
        }

    private class Arrangement {
        val messageRepository = mock<MessageRepository>()
        val notificationEventsManager = mock<NotificationEventsManager>(mode = MockMode.autoUnit)

        fun arrange() = this to PersistMessageUseCaseImpl(
            messageRepository = messageRepository,
            selfUserId = TestUser.USER_ID,
            notificationEventsManager = notificationEventsManager
        )

        fun withPersistMessageSuccess() = apply {
            everySuspend {
                messageRepository.persistMessage(any(), any())
            } returns Either.Right(InsertMessageResult.INSERTED_NEED_TO_NOTIFY_USER)
        }

        fun withPersistMessageFailure() = apply {
            everySuspend {
                messageRepository.persistMessage(any(), any())
            } returns Either.Left(CoreFailure.InvalidEventSenderID)
        }

        fun withReceiptMode() = apply {
            everySuspend {
                messageRepository.getReceiptModeFromGroupConversationByQualifiedID(any())
            } returns Either.Right(Conversation.ReceiptMode.ENABLED)
        }
    }
}

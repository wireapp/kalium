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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.persistence.dao.message.InsertMessageResult
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
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

        coVerify {
            arrangement.messageRepository.persistMessage(any(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.messageRepository.getReceiptModeFromGroupConversationByQualifiedID(any())
        }.wasInvoked(once)
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

            coVerify {
                arrangement.messageRepository.persistMessage(any(), any())
            }.wasInvoked(once)

            coVerify {
                arrangement.messageRepository.getReceiptModeFromGroupConversationByQualifiedID(any())
            }.wasInvoked(once)

            coVerify {
                arrangement.notificationEventsManager.scheduleRegularNotificationChecking()
            }.wasInvoked(once)
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

            coVerify {
                arrangement.messageRepository.persistMessage(any(), any())
            }.wasInvoked(once)

            coVerify {
                arrangement.messageRepository.getReceiptModeFromGroupConversationByQualifiedID(any())
            }.wasInvoked(once)

            coVerify {
                arrangement.notificationEventsManager.scheduleRegularNotificationChecking()
            }.wasNotInvoked()
        }

    private class Arrangement {
        @Mock
        val messageRepository = mock(MessageRepository::class)

        @Mock
        val notificationEventsManager = mock(NotificationEventsManager::class)

        fun arrange() = this to PersistMessageUseCaseImpl(
            messageRepository = messageRepository,
            selfUserId = TestUser.USER_ID,
            notificationEventsManager = notificationEventsManager
        )

        suspend fun withPersistMessageSuccess() = apply {
            coEvery {
                messageRepository.persistMessage(any(), any())
            }.returns(Either.Right(InsertMessageResult.INSERTED_NEED_TO_NOTIFY_USER))
        }

        suspend fun withPersistMessageFailure() = apply {
            coEvery {
                messageRepository.persistMessage(any(), any())
            }.returns(Either.Left(CoreFailure.InvalidEventSenderID))
        }

        suspend fun withReceiptMode() = apply {
            coEvery {
                messageRepository.getReceiptModeFromGroupConversationByQualifiedID(any())
            }.returns(Either.Right(Conversation.ReceiptMode.ENABLED))
        }
    }
}

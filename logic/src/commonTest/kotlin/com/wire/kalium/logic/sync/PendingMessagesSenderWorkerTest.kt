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
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class PendingMessagesSenderWorkerTest {

    private val messageRepository = mock<MessageRepository>()
    private val messageSender = mock<MessageSender>()

    private lateinit var pendingMessagesSenderWorker: PendingMessagesSenderWorker

    @BeforeTest
    fun setup() {
        pendingMessagesSenderWorker = PendingMessagesSenderWorker(messageRepository, messageSender, TestUser.USER_ID)
    }

    @Test
    fun givenPendingMessagesAreFetched_whenExecutingAWorker_thenScheduleSendingOfMessages() = runTest {
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
    }
}

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

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class PendingMessagesSenderWorkerTest {

    @Mock
    private val messageRepository = mock(MessageRepository::class)

    @Mock
    private val messageSender = mock(MessageSender::class)

    private lateinit var pendingMessagesSenderWorker: PendingMessagesSenderWorker

    @BeforeTest
    fun setup() {
        pendingMessagesSenderWorker = PendingMessagesSenderWorker(messageRepository, messageSender, TestUser.USER_ID)
    }

    @Test
    fun givenPendingMessagesAreFetched_whenExecutingAWorker_thenScheduleSendingOfMessages() = runTest {
        val message = TestMessage.TEXT_MESSAGE
        coEvery {
            messageRepository.getAllPendingMessagesFromUser(eq(TestUser.USER_ID))
        }.returns(Either.Right(listOf(message)))
        coEvery {
            messageSender.sendPendingMessage(eq(message.conversationId), eq(message.id))
        }.returns(Either.Right(Unit))

        pendingMessagesSenderWorker.doWork()

        coVerify {
            messageSender.sendPendingMessage(eq(message.conversationId), eq(message.id))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenPendingMessagesReturnsFailure_whenExecutingAWorker_thenDoNothing() = runTest {
        val dataNotFoundFailure = StorageFailure.DataNotFound
        coEvery {
            messageRepository.getAllPendingMessagesFromUser(eq(TestUser.USER_ID))
        }.returns(Either.Left(dataNotFoundFailure))

        pendingMessagesSenderWorker.doWork()

        coVerify {
            messageSender.sendPendingMessage(any(), any())
        }.wasNotInvoked()
    }
}

/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PendingMessagesSenderWorkerTest {

    @Mock
    val messageRepository = configure(mock(MessageRepository::class)) { stubsUnitByDefault = true }

    @Mock
    val messageSender = configure(mock(MessageSender::class)) { stubsUnitByDefault = true }

    private lateinit var pendingMessagesSenderWorker: PendingMessagesSenderWorker

    @BeforeTest
    fun setup() {
        pendingMessagesSenderWorker = PendingMessagesSenderWorker(messageRepository, messageSender, TestUser.USER_ID)
    }

    @Test
    fun givenPendingMessagesAreFetched_whenExecutingAWorker_thenScheduleSendingOfMessages() = runTest {
        val message = TestMessage.TEXT_MESSAGE
        given(messageRepository)
            .suspendFunction(messageRepository::getAllPendingMessagesFromUser)
            .whenInvokedWith(eq(TestUser.USER_ID))
            .thenReturn(Either.Right(listOf(message)))
        given(messageSender)
            .suspendFunction(messageSender::sendPendingMessage)
            .whenInvokedWith(eq(message.conversationId), eq(message.id))
            .thenReturn(Either.Right(Unit))

        pendingMessagesSenderWorker.doWork()

        verify(messageSender)
            .suspendFunction(messageSender::sendPendingMessage)
            .with(eq(message.conversationId), eq(message.id))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenPendingMessagesReturnsFailure_whenExecutingAWorker_thenDoNothing() = runTest {
        val dataNotFoundFailure = StorageFailure.DataNotFound
        given(messageRepository)
            .suspendFunction(messageRepository::getAllPendingMessagesFromUser)
            .whenInvokedWith(eq(TestUser.USER_ID))
            .thenReturn(Either.Left(dataNotFoundFailure))

        pendingMessagesSenderWorker.doWork()

        verify(messageSender)
            .suspendFunction(messageSender::sendPendingMessage)
            .with(any(), any())
            .wasNotInvoked()
    }
}

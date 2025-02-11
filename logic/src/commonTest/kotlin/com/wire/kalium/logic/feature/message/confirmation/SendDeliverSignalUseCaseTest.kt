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
package com.wire.kalium.logic.feature.message.confirmation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.kaliumLogger
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class SendDeliverSignalUseCaseTest {

    @Test
    fun givenValidClientIdAndMessage_whenInvoking_thenShouldSendMessageSuccessfully() = runTest {
        val (arrangement, usecase) = Arrangement()
            .withCurrentClientIdProvider()
            .withMessageSenderResult()
            .arrange()

        val conversation = TestConversation.CONVERSATION
        val messageIdList = listOf(TestMessage.TEST_MESSAGE_ID)

        val result = usecase.invoke(conversation, messageIdList)

        assertTrue(result is Either.Right)
        coVerify { arrangement.messageSender.sendMessage(any(), any()) }.wasInvoked()
    }

    @Test
    fun givenMessageSendingFailure_whenInvoking_thenShouldLogError() = runTest {
        val (arrangement, usecase) = Arrangement()
            .withCurrentClientIdProvider()
            .withMessageSenderResult(Either.Left(CoreFailure.Unknown(RuntimeException("Sending failed"))))
            .arrange()

        val conversation = TestConversation.CONVERSATION
        val messageIdList = listOf(TestMessage.TEST_MESSAGE_ID)

        val result = usecase.invoke(conversation, messageIdList)

        assertTrue(result is Either.Left)
        coVerify { arrangement.messageSender.sendMessage(any(), any()) }.wasInvoked()
    }

    @Test
    fun givenClientIdProviderFailure_whenInvoking_thenShouldReturnFailure() = runTest {
        val (arrangement, usecase) = Arrangement()
            .withCurrentClientIdProviderError()
            .arrange()

        val conversation = TestConversation.CONVERSATION
        val messageIdList = listOf(TestMessage.TEST_MESSAGE_ID)

        val result = usecase.invoke(conversation, messageIdList)

        assertTrue(result is Either.Left)
        coVerify { arrangement.messageSender.sendMessage(any(), any()) }.wasNotInvoked()
    }

    private class Arrangement {

        @Mock
        private val currentClientIdProvider = mock(CurrentClientIdProvider::class)

        @Mock
        val messageSender = mock(MessageSender::class)

        suspend fun withCurrentClientIdProvider() = apply {
            coEvery { currentClientIdProvider.invoke() }.returns(Either.Right(TestClient.CLIENT_ID))
        }

        suspend fun withCurrentClientIdProviderError() = apply {
            coEvery { currentClientIdProvider.invoke() }.returns(
                Either.Left(
                    CoreFailure.Unknown(
                        RuntimeException("Client ID not available")
                    )
                )
            )
        }

        suspend fun withMessageSenderResult(result: Either<CoreFailure, Unit> = Unit.right()) = apply {
            coEvery { messageSender.sendMessage(any(), any()) }.returns(result)
        }

        fun arrange() = this to SendDeliverSignalUseCaseImpl(
            selfUserId = TestClient.USER_ID,
            currentClientIdProvider = currentClientIdProvider,
            messageSender = messageSender,
            kaliumLogger = kaliumLogger,
        )
    }
}

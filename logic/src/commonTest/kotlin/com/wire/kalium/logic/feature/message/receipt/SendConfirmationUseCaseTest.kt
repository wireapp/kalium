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

package com.wire.kalium.logic.feature.message.receipt

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class SendConfirmationUseCaseTest {

    @Test
    fun givenAConversationId_whenReadConfirmationsEnabled_thenShouldSendConfirmation() = runTest {
        val (arrangement, sendConfirmation) = Arrangement()
            .withCurrentClientIdProvider()
            .withGetConversationByIdSuccessful()
            .withToggleReadReceiptsStatus(true)
            .withPendingMessagesResponse()
            .withSendMessageSuccess()
            .arrange()

        val after = Instant.DISTANT_PAST
        val until = after + 10.seconds

        val result = sendConfirmation(TestConversation.ID, after, until)

        result.shouldSucceed()
        coVerify {
            arrangement.messageSender.sendMessage(any(), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationId_whenReadConfirmationsDisabled_thenShouldNOTSendConfirmation() = runTest {
        val (arrangement, sendConfirmation) = Arrangement()
            .withCurrentClientIdProvider()
            .withGetConversationByIdSuccessful()
            .withToggleReadReceiptsStatus(false)
            .withPendingMessagesResponse()
            .withSendMessageSuccess()
            .arrange()

        val after = Instant.DISTANT_PAST
        val until = after + 10.seconds

        val result = sendConfirmation(TestConversation.ID, after, until)

        result.shouldSucceed()
        coVerify {
            arrangement.messageSender.sendMessage(any(), any())
        }.wasNotInvoked()

        coVerify {
            arrangement.messageRepository.getPendingConfirmationMessagesByConversationAfterDate(
                any(),
                eq(after),
                eq(until),
                any()
            )
        }.wasNotInvoked()
    }

    private class Arrangement {

        @Mock
        private val currentClientIdProvider = mock(CurrentClientIdProvider::class)

        @Mock
        private val syncManager = mock(SyncManager::class)

        @Mock
        val messageSender = mock(MessageSender::class)

        @Mock
        private val conversationRepository = mock(ConversationRepository::class)

        @Mock
        val messageRepository = mock(MessageRepository::class)

        @Mock
        private val userPropertyRepository = mock(UserPropertyRepository::class)

        suspend fun withCurrentClientIdProvider() = apply {
            coEvery {
                currentClientIdProvider.invoke()
            }.returns(Either.Right(TestClient.CLIENT_ID))
        }

        suspend fun withSendMessageSuccess() = apply {
            coEvery {
                messageSender.sendMessage(any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withGetConversationByIdSuccessful() = apply {
            coEvery {
                conversationRepository.getConversationById(any())
            }.returns(Either.Right(TestConversation.CONVERSATION))
        }

        suspend fun withToggleReadReceiptsStatus(enabled: Boolean = false) = apply {
            coEvery {
                userPropertyRepository.getReadReceiptsStatus()
            }.returns(enabled)
        }

        suspend fun withPendingMessagesResponse() = apply {
            coEvery {
                messageRepository.getPendingConfirmationMessagesByConversationAfterDate(any(), any(), any(), any())
            }.returns(Either.Right(listOf(TestMessage.TEXT_MESSAGE.id)))
        }

        fun arrange() = this to SendConfirmationUseCase(
            currentClientIdProvider,
            syncManager,
            messageSender,
            TestUser.SELF.id,
            conversationRepository,
            messageRepository,
            userPropertyRepository
        )
    }

}

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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.SelfDeletionTimer
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.ObserveSelfDeletionTimerSettingsForConversationUseCaseArrangement
import com.wire.kalium.logic.util.arrangement.ObserveSelfDeletionTimerSettingsForConversationUseCaseArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.every
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.time.Duration

class SendKnockUserCaseTest {

    @Test
    fun givenAValidSendKnockRequest_whenSendingKnock_thenShouldReturnASuccessResult() = runTest {
        // Given
        val conversationId = ConversationId("some-convo-id", "some-domain-id")
        val (arrangement, sendKnockUseCase) = Arrangement()
            .withCurrentClientProviderSuccess()
            .withPersistMessageSuccess()
            .withSlowSyncStatusComplete()
            .withSendMessageSuccess()
            .arrange {
                withConversationTimer(flowOf(SelfDeletionTimer.Disabled))
            }

        // When
        val result = sendKnockUseCase.invoke(conversationId, false)

        // Then
        result.shouldSucceed()
        coVerify {
            arrangement.messageSender.sendMessage(any(), any())
        }.wasInvoked(once)
        coVerify {
            arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenNoNetwork_whenSendingKnock_thenShouldReturnAFailure() = runTest {
        // Given
        val conversationId = ConversationId("some-convo-id", "some-domain-id")
        val (arrangement, sendKnockUseCase) = Arrangement()
            .withCurrentClientProviderSuccess()
            .withPersistMessageSuccess()
            .withSlowSyncStatusComplete()
            .withSendMessageFailure()
            .arrange {
                withConversationTimer(flowOf(SelfDeletionTimer.Disabled))
            }

        // When
        val result = sendKnockUseCase.invoke(conversationId, false)

        // Then
        result.shouldFail()
        coVerify {
            arrangement.messageSender.sendMessage(any(), any())
        }.wasInvoked(once)
        coVerify {
            arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), any())
        }.wasInvoked(once)
    }

    @Test
    fun givenConversationHasTimer_whenSendingKnock_thenTheTimerIsAdded() = runTest {
        // Given
        val expectedDuration = Duration.parse("PT1H")
        val conversationId = ConversationId("some-convo-id", "some-domain-id")
        val (arrangement, sendKnockUseCase) = Arrangement()
            .withCurrentClientProviderSuccess()
            .withPersistMessageSuccess()
            .withSlowSyncStatusComplete()
            .withSendMessageSuccess()
            .arrange {
                withConversationTimer(flowOf(SelfDeletionTimer.Enabled(expectedDuration)))
            }

        // When
        val result = sendKnockUseCase.invoke(conversationId, false)

        // Then
        result.shouldSucceed()
        coVerify {
            arrangement.messageSender.sendMessage(
                message = matches {
                    assertIs<Message.Regular>(it)
                    it.expirationData?.expireAfter == expectedDuration
                },
                messageTarget = any()
            )
        }.wasInvoked(once)
        coVerify {
            arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), any())
        }.wasNotInvoked()
    }

    private class Arrangement :
        ObserveSelfDeletionTimerSettingsForConversationUseCaseArrangement by ObserveSelfDeletionTimerSettingsForConversationUseCaseArrangementImpl() {

        @Mock
        private val persistMessage = mock(PersistMessageUseCase::class)

        @Mock
        val currentClientIdProvider = mock(CurrentClientIdProvider::class)

        @Mock
        private val slowSyncRepository = mock(SlowSyncRepository::class)

        @Mock
        val messageSender = mock(MessageSender::class)

        @Mock
        val messageSendFailureHandler = mock(MessageSendFailureHandler::class)

        suspend fun withSendMessageSuccess() = apply {
            coEvery {
                messageSender.sendMessage(any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withSendMessageFailure() = apply {
            coEvery {
                messageSender.sendMessage(any(), any())
            }.returns(Either.Left(NetworkFailure.NoNetworkConnection(null)))
        }

        suspend fun withCurrentClientProviderSuccess(clientId: ClientId = TestClient.CLIENT_ID) = apply {
            coEvery {
                currentClientIdProvider.invoke()
            }.returns(Either.Right(clientId))
        }

        suspend fun withPersistMessageSuccess() = apply {
            coEvery {
                persistMessage.invoke(any())
            }.returns(Either.Right(Unit))
        }

        fun withSlowSyncStatusComplete() = apply {
            val stateFlow = MutableStateFlow<SlowSyncStatus>(SlowSyncStatus.Complete).asStateFlow()
            every {
                slowSyncRepository.slowSyncStatus
            }.returns(stateFlow)
        }

        fun arrange(block: suspend (Arrangement.() -> Unit) = {}): Pair<Arrangement, SendKnockUseCase> {
            runBlocking { block() }
            return this to SendKnockUseCase(
                persistMessage,
                TestUser.SELF.id,
                currentClientIdProvider,
                slowSyncRepository,
                messageSender,
                messageSendFailureHandler,
                observeSelfDeletionTimerSettingsForConversation
            )
        }
    }

}

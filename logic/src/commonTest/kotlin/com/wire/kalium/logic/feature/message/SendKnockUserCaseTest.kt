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

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
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
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCase
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.util.KaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.matching
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
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
        val (arrangement, sendKnockUseCase) = Arrangement(testKaliumDispatcher)
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
        assertIs<MessageOperationResult.Success>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun givenNoNetwork_whenSendingKnock_thenShouldReturnAFailure() = runTest {
        // Given
        val conversationId = ConversationId("some-convo-id", "some-domain-id")
        val (arrangement, sendKnockUseCase) = Arrangement(testKaliumDispatcher)
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
        assertIs<MessageOperationResult.Failure>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(any(), any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun givenConversationHasTimer_whenSendingKnock_thenTheTimerIsAdded() = runTest {
        // Given
        val expectedDuration = Duration.parse("PT1H")
        val conversationId = ConversationId("some-convo-id", "some-domain-id")
        val (arrangement, sendKnockUseCase) = Arrangement(testKaliumDispatcher)
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
        assertIs<MessageOperationResult.Success>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(
                message = matching {
                    assertIs<Message.Regular>(it)
                    it.expirationData?.expireAfter == expectedDuration
                },
                messageTarget = any()
            )
        }
        verifySuspend(VerifyMode.not) {
            arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), any())
        }
    }

    private class Arrangement(var dispatcher: KaliumDispatcher = TestKaliumDispatcher) {
        private val persistMessage = mock<PersistMessageUseCase>(mode = MockMode.autoUnit)
        val currentClientIdProvider = mock<CurrentClientIdProvider>(mode = MockMode.autoUnit)
        private val slowSyncRepository = mock<SlowSyncRepository>(mode = MockMode.autoUnit)
        val messageSender = mock<MessageSender>(mode = MockMode.autoUnit)
        val messageSendFailureHandler = mock<MessageSendFailureHandler>(mode = MockMode.autoUnit)
        val observeSelfDeletionTimerSettingsForConversation =
            mock<ObserveSelfDeletionTimerSettingsForConversationUseCase>(mode = MockMode.autoUnit)

        suspend fun withSendMessageSuccess() = apply {
            everySuspend {
                messageSender.sendMessage(any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withSendMessageFailure() = apply {
            everySuspend {
                messageSender.sendMessage(any(), any())
            } returns Either.Left(NetworkFailure.NoNetworkConnection(null))
        }

        suspend fun withCurrentClientProviderSuccess(clientId: ClientId = TestClient.CLIENT_ID) = apply {
            everySuspend {
                currentClientIdProvider.invoke()
            } returns Either.Right(clientId)
        }

        suspend fun withPersistMessageSuccess() = apply {
            everySuspend {
                persistMessage.invoke(any())
            } returns Either.Right(Unit)
        }

        fun withSlowSyncStatusComplete() = apply {
            val stateFlow = MutableStateFlow<SlowSyncStatus>(SlowSyncStatus.Complete).asStateFlow()
            every {
                slowSyncRepository.slowSyncStatus
            } returns stateFlow
        }

        suspend fun withConversationTimer(result: Flow<SelfDeletionTimer>) {
            everySuspend {
                observeSelfDeletionTimerSettingsForConversation(any(), any())
            } returns result
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
                observeSelfDeletionTimerSettingsForConversation,
                dispatcher
            )
        }
    }

}

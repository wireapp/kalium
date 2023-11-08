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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.SelfDeletionTimer
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.ObserveSelfDeletionTimerSettingsForConversationUseCaseArrangement
import com.wire.kalium.logic.util.arrangement.ObserveSelfDeletionTimerSettingsForConversationUseCaseArrangementImpl
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
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
        assertTrue(result is Either.Right)
        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendMessage)
            .with(any(), any())
            .wasInvoked(once)
        verify(arrangement.messageSendFailureHandler)
            .suspendFunction(arrangement.messageSendFailureHandler::handleFailureAndUpdateMessageStatus)
            .with(any(), any(), any(), any(), any())
            .wasNotInvoked()
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
        assertTrue(result is Either.Left)
        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendMessage)
            .with(any(), any())
            .wasInvoked(once)
        verify(arrangement.messageSendFailureHandler)
            .suspendFunction(arrangement.messageSendFailureHandler::handleFailureAndUpdateMessageStatus)
            .with(any(), any(), any(), any(), any())
            .wasInvoked(once)
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
        assertTrue(result is Either.Right)
        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendMessage)
            .with(matching {
                assertIs<Message.Regular>(it)
                           it.expirationData?.expireAfter == expectedDuration
            }, any())
            .wasInvoked(once)
        verify(arrangement.messageSendFailureHandler)
            .suspendFunction(arrangement.messageSendFailureHandler::handleFailureAndUpdateMessageStatus)
            .with(any(), any(), any(), any(), any())
            .wasNotInvoked()
    }


    private class Arrangement :
        ObserveSelfDeletionTimerSettingsForConversationUseCaseArrangement by ObserveSelfDeletionTimerSettingsForConversationUseCaseArrangementImpl() {

        @Mock
        private val persistMessage = mock(classOf<PersistMessageUseCase>())

        @Mock
        val currentClientIdProvider = mock(classOf<CurrentClientIdProvider>())

        @Mock
        private val slowSyncRepository = mock(classOf<SlowSyncRepository>())

        @Mock
        val messageSender = mock(classOf<MessageSender>())

        @Mock
        val messageSendFailureHandler = configure(mock(classOf<MessageSendFailureHandler>())) { stubsUnitByDefault = true }


        fun withSendMessageSuccess() = apply {
            given(messageSender)
                .suspendFunction(messageSender::sendMessage)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
        }

        fun withSendMessageFailure() = apply {
            given(messageSender)
                .suspendFunction(messageSender::sendMessage)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Left(NetworkFailure.NoNetworkConnection(null)))
        }

        fun withCurrentClientProviderSuccess(clientId: ClientId = TestClient.CLIENT_ID) = apply {
            given(currentClientIdProvider)
                .suspendFunction(currentClientIdProvider::invoke)
                .whenInvoked()
                .thenReturn(Either.Right(clientId))
        }

        fun withPersistMessageSuccess() = apply {
            given(persistMessage)
                .suspendFunction(persistMessage::invoke)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withSlowSyncStatusComplete() = apply {
            val stateFlow = MutableStateFlow<SlowSyncStatus>(SlowSyncStatus.Complete).asStateFlow()
            given(slowSyncRepository)
                .getter(slowSyncRepository::slowSyncStatus)
                .whenInvoked()
                .thenReturn(stateFlow)
        }

        fun arrange(block: (Arrangement.() -> Unit) = {}): Pair<Arrangement, SendKnockUseCase> {
            block()
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

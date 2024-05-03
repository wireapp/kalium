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
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.SelfDeletionTimer
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCase
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.testKaliumDispatcher
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SendTextMessageCaseTest {

    @Test
    fun givenAValidMessage_whenSendingSomeText_thenShouldReturnASuccessResult() = runTest {
        // Given
        val (arrangement, sendTextMessage) = Arrangement(this)
            .withToggleReadReceiptsStatus()
            .withCurrentClientProviderSuccess()
            .withPersistMessageSuccess()
            .withSlowSyncStatusComplete()
            .withMessageTimer(SelfDeletionTimer.Disabled)
            .withSendMessageSuccess()
            .arrange()

        // When
        val result = sendTextMessage(TestConversation.ID, "some-text")

        // Then
        result.shouldSucceed()

        coVerify {
            arrangement.userPropertyRepository.getReadReceiptsStatus()
        }.wasInvoked(once)
        coVerify {
            arrangement.persistMessage.invoke(matches { message -> message.content is MessageContent.Text })
        }.wasInvoked(once)
        coVerify {
            arrangement.messageSender.sendMessage(
                matches { message -> message.content is MessageContent.Text },
                any()
            )
        }.wasInvoked(once)
        coVerify {
            arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenNoNetwork_whenSendingSomeText_thenShouldReturnAFailure() = runTest {
        // Given
        val (arrangement, sendTextMessage) = Arrangement(this)
            .withToggleReadReceiptsStatus()
            .withCurrentClientProviderSuccess()
            .withPersistMessageSuccess()
            .withSlowSyncStatusComplete()
            .withSendMessageFailure()
            .withMessageTimer(SelfDeletionTimer.Disabled)
            .arrange()

        // When
        val result = sendTextMessage(TestConversation.ID, "some-text")

        // Then
        result.shouldFail()

        coVerify {
            arrangement.userPropertyRepository.getReadReceiptsStatus()
        }.wasInvoked(once)
        coVerify {
            arrangement.persistMessage.invoke(any())
        }.wasInvoked(once)
        coVerify {
            arrangement.messageSender.sendMessage(any(), any())
        }.wasInvoked(once)
        coVerify {
            arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), any())
        }.wasInvoked(once)
    }

    private class Arrangement(private val coroutineScope: CoroutineScope) {

        @Mock
        val persistMessage = mock(PersistMessageUseCase::class)

        @Mock
        val currentClientIdProvider = mock(CurrentClientIdProvider::class)

        @Mock
        val slowSyncRepository = mock(SlowSyncRepository::class)

        @Mock
        val messageSender = mock(MessageSender::class)

        @Mock
        val userPropertyRepository = mock(UserPropertyRepository::class)

        @Mock
        val messageSendFailureHandler = mock(MessageSendFailureHandler::class)

        @Mock
        val observeSelfDeletionTimerSettingsForConversation = mock(ObserveSelfDeletionTimerSettingsForConversationUseCase::class)

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

        suspend fun withToggleReadReceiptsStatus(enabled: Boolean = false) = apply {
            coEvery {
                userPropertyRepository.getReadReceiptsStatus()
            }.returns(enabled)
        }

        suspend fun withMessageTimer(result: SelfDeletionTimer) = apply {
            coEvery {
                observeSelfDeletionTimerSettingsForConversation.invoke(any(), any())
            }.returns(flowOf(result))
        }

        fun arrange() = this to SendTextMessageUseCase(
            persistMessage,
            TestUser.SELF.id,
            currentClientIdProvider,
            slowSyncRepository,
            messageSender,
            messageSendFailureHandler,
            userPropertyRepository,
            observeSelfDeletionTimerSettingsForConversation,
            scope = coroutineScope,
            dispatchers = coroutineScope.testKaliumDispatcher
        )
    }

}

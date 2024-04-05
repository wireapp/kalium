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
package com.wire.kalium.logic.feature.message.composite

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.feature.message.MessageSendFailureHandler
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.configure
import io.mockative.every
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SendButtonMessageCaseTest {

    @Test
    fun givenATextMessageContainsButtons_whenSendingIt_thenShouldBeCompositeAndReturnASuccessResult() = runTest {
        // Given
        val (arrangement, sendTextMessage) = SendButtonMessageCaseTest.Arrangement(this)
            .withToggleReadReceiptsStatus()
            .withCurrentClientProviderSuccess()
            .withPersistMessageSuccess()
            .withSlowSyncStatusComplete()
            .withSendMessageSuccess()
            .arrange()
        val buttons = listOf("OK", "Cancel")

        // When
        val result = sendTextMessage.invoke(TestConversation.ID, "some-text", listOf(), null, buttons)

        // Then
        result.shouldSucceed()

        coVerify {
            arrangement.userPropertyRepository.getReadReceiptsStatus()
        }.wasInvoked(once)
        coVerify {
            arrangement.persistMessage.invoke(matches { message -> message.content is MessageContent.Composite })
        }.wasInvoked(once)
        coVerify {
            arrangement.messageSender.sendMessage(
                matches { message -> message.content is MessageContent.Composite },
                any()
            )
        }.wasInvoked(once)
        coVerify {
            arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), any())
        }.wasNotInvoked()
    }

    private class Arrangement(private val coroutineScope: CoroutineScope) {

        @Mock
        val persistMessage = mock(classOf<PersistMessageUseCase>())

        @Mock
        val currentClientIdProvider = mock(classOf<CurrentClientIdProvider>())

        @Mock
        val slowSyncRepository = mock(classOf<SlowSyncRepository>())

        @Mock
        val messageSender = mock(classOf<MessageSender>())

        @Mock
        val userPropertyRepository = mock(classOf<UserPropertyRepository>())

        @Mock
        val messageSendFailureHandler = configure(mock(classOf<MessageSendFailureHandler>())) { }

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

        fun arrange() = this to SendButtonMessageUseCase(
            persistMessage,
            TestUser.SELF.id,
            currentClientIdProvider,
            slowSyncRepository,
            messageSender,
            messageSendFailureHandler,
            userPropertyRepository,
            scope = coroutineScope
        )
    }
}

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

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.feature.message.MessageSendFailureHandler
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.util.InternalKaliumApi
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.matching
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(InternalKaliumApi::class)
class SendButtonMessageCaseTest {

    @Test
    fun givenATextMessageContainsButtons_whenSendingIt_thenShouldBeCompositeAndReturnASuccessResult() = runTest {
        val (arrangement, sendTextMessage) = Arrangement(this)
            .withToggleReadReceiptsStatus()
            .withCurrentClientProviderSuccess()
            .withPersistMessageSuccess()
            .withSlowSyncStatusComplete()
            .withSendMessageSuccess()
            .arrange()
        val buttons = listOf("OK", "Cancel")

        val result = sendTextMessage.invoke(TestConversation.ID, "some-text", listOf(), null, buttons)

        result.toEither().shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userPropertyRepository.getReadReceiptsStatus()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage.invoke(matching { message -> message.content is MessageContent.Composite })
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(
                matching { message -> message.content is MessageContent.Composite },
                any()
            )
        }
        verifySuspend(VerifyMode.not) {
            arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), any())
        }
    }

    private class Arrangement(private val coroutineScope: CoroutineScope) {
        val persistMessage = mock<PersistMessageUseCase>(mode = MockMode.autoUnit)
        val currentClientIdProvider = mock<CurrentClientIdProvider>(mode = MockMode.autoUnit)
        val slowSyncRepository = mock<SlowSyncRepository>(mode = MockMode.autoUnit)
        val messageSender = mock<MessageSender>(mode = MockMode.autoUnit)
        val userPropertyRepository = mock<UserPropertyRepository>(mode = MockMode.autoUnit)
        val messageSendFailureHandler = mock<MessageSendFailureHandler>(mode = MockMode.autoUnit)

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

        suspend fun withToggleReadReceiptsStatus(enabled: Boolean = false) = apply {
            everySuspend {
                userPropertyRepository.getReadReceiptsStatus()
            } returns enabled
        }

        fun arrange() = this to SendButtonMessageUseCase(
            persistMessage,
            TestUser.SELF.id,
            currentClientIdProvider,
            slowSyncRepository,
            messageSender,
            messageSendFailureHandler,
            userPropertyRepository,
            scope = coroutineScope,
            dispatchers = coroutineScope.testKaliumDispatcher
        )
    }
}

/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.message.poll

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.feature.message.MessageOperationResult
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.messaging.sending.MessageSendFailureHandler
import com.wire.kalium.messaging.sending.MessageSender
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class SendPollMessageUseCaseTest {

    @Test
    fun givenValidPoll_whenSending_thenTrimmedPollIsPersistedAndSent() = runTest {
        val arrangement = arrangement()

        val result = arrangement.useCase(TestConversation.ID, " Lunch? ", listOf(" Pizza", "Sushi "), true)

        assertIs<MessageOperationResult.Success>(result)
        verifySuspend {
            arrangement.persistMessage(
                matches {
                    val poll = (it as Message.Regular).content as MessageContent.Poll
                    poll.question == "Lunch?" && poll.options.map { option -> option.text } == listOf("Pizza", "Sushi") &&
                            poll.allowMultipleAnswers && it.expirationData == null
                }
            )
            arrangement.messageSender.sendMessage(any(), any())
        }
    }

    @Test
    fun givenInvalidPoll_whenSending_thenNothingIsPersisted() = runTest {
        val arrangement = arrangement()

        listOf(
            "  " to listOf("Pizza", "Sushi"),
            "Lunch?" to listOf("Pizza"),
            "Lunch?" to listOf("Pizza", " "),
            "Lunch?" to listOf("Pizza", "pizza "),
            "Lunch?" to List(SendPollMessageUseCase.MAX_OPTIONS + 1) { "Option $it" },
        ).forEach { (question, options) ->
            assertIs<MessageOperationResult.Failure>(arrangement.useCase(TestConversation.ID, question, options, false))
        }

        verifySuspend(VerifyMode.not) { arrangement.persistMessage(any()) }
    }

    @Test
    fun givenSendingFails_whenSending_thenFailureIsHandledAndReturned() = runTest {
        val arrangement = arrangement().withSendReturning(Either.Left(NetworkFailure.NoNetworkConnection(null)))

        val result = arrangement.useCase(TestConversation.ID, "Lunch?", listOf("Pizza", "Sushi"), false)

        assertIs<MessageOperationResult.Failure>(result)
        verifySuspend {
            arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), any())
        }
    }

    private fun TestScope.arrangement() = Arrangement(this)

    private class Arrangement(testScope: TestScope) {
        val persistMessage = mock<PersistMessageUseCase>(MockMode.autoUnit)
        val messageSender = mock<MessageSender>(MockMode.autoUnit)
        val messageSendFailureHandler = mock<MessageSendFailureHandler>(MockMode.autoUnit)
        private val slowSyncRepository = mock<SlowSyncRepository>(MockMode.autoUnit)
        private val userPropertyRepository = mock<UserPropertyRepository>(MockMode.autoUnit)

        val useCase = SendPollMessageUseCase(
            persistMessage = persistMessage,
            selfUserId = TestUser.SELF.id,
            currentClientIdProvider = CurrentClientIdProvider { Either.Right(TestClient.CLIENT_ID) },
            slowSyncRepository = slowSyncRepository,
            messageSender = messageSender,
            messageSendFailureHandler = messageSendFailureHandler,
            userPropertyRepository = userPropertyRepository,
            dispatcher = StandardTestDispatcher(testScope.testScheduler).testKaliumDispatcher(),
            scope = testScope.backgroundScope
        )

        init {
            every { slowSyncRepository.slowSyncStatus } returns MutableStateFlow(SlowSyncStatus.Complete)
            everySuspend { userPropertyRepository.getReadReceiptsStatus() } returns false
            everySuspend { persistMessage(any()) } returns Either.Right(Unit)
            withSendReturning(Either.Right(Unit))
        }

        fun withSendReturning(result: Either<NetworkFailure, Unit>) = apply {
            everySuspend { messageSender.sendMessage(any(), any()) } returns result
        }
    }
}

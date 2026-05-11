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
package com.wire.kalium.logic.feature.conversation.messagetimer

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.common.functional.Either
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class UpdateMessageTimerUseCaseTest {

    @Test
    fun givenConversationAndTimer_WhenUpdateMessageTimerIsSuccessful_ThenReturnSuccess() = runTest {
        val (arrangement, updateMessageTimerUseCase) = Arrangement()
            .withUpdateMessageTimer(Either.Right(Unit))
            .arrange()

        val messageTimer = 5000L

        val result = updateMessageTimerUseCase(TestConversation.ID, messageTimer)

        assertIs<UpdateMessageTimerUseCase.Result.Success>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationGroupRepository.updateMessageTimer(eq(TestConversation.ID), eq(messageTimer))
        }
    }

    @Test
    fun givenConversationAndTimer_WhenUpdateMessageTimerFailed_ThenReturnFailure() = runTest {
        val (arrangement, updateMessageTimerUseCase) = Arrangement()
            .withUpdateMessageTimer(Either.Left(StorageFailure.DataNotFound))
            .arrange()

        val messageTimer = 5000L

        val result = updateMessageTimerUseCase(TestConversation.ID, messageTimer)
        assertIs<UpdateMessageTimerUseCase.Result.Failure>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationGroupRepository.updateMessageTimer(eq(TestConversation.ID), eq(messageTimer))
        }
    }

    private class Arrangement {

        val conversationGroupRepository = mock<ConversationGroupRepository>(mode = MockMode.autoUnit)

        private val updateMessageTimerUseCase = UpdateMessageTimerUseCaseImpl(
            conversationGroupRepository
        )

        suspend fun withUpdateMessageTimer(either: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                conversationGroupRepository.updateMessageTimer(any(), any())
            } returns either
        }

        fun arrange() = this to updateMessageTimerUseCase
    }
}

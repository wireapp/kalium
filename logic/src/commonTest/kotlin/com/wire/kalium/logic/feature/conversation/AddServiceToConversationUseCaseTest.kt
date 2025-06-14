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
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.service.ServiceId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.common.functional.Either
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class AddServiceToConversationUseCaseTest {

    @Test
    fun givenMemberAndConversation_WhenAddMemberIsSuccessful_ThenReturnSuccess() = runTest {
        val serviceId = ServiceId("serviceId", "provider")
        val (arrangement, addService) = Arrangement()
            .withAddService(Either.Right(Unit))
            .arrange()

        val result = addService(TestConversation.ID, serviceId)

        assertIs<AddServiceToConversationUseCase.Result.Success>(result)

        coVerify {
            arrangement.conversationGroupRepository.addService(eq(serviceId), eq(TestConversation.ID))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberAndConversation_WhenAddMemberFailed_ThenReturnFailure() = runTest {
        val serviceId = ServiceId("serviceId", "provider")

        val (arrangement, addService) = Arrangement()
            .withAddService(Either.Left(MLSFailure.Generic(UnsupportedOperationException())))
            .arrange()

        val result = addService(TestConversation.ID, serviceId)
        assertIs<AddServiceToConversationUseCase.Result.Failure>(result)
        assertIs<MLSFailure.Generic>(result.cause)

        coVerify {
            arrangement.conversationGroupRepository.addService(eq(serviceId), eq(TestConversation.ID))
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {

        val conversationGroupRepository = mock(ConversationGroupRepository::class)

        private val addService = AddServiceToConversationUseCase(
            conversationGroupRepository
        )

        suspend fun withAddService(either: Either<CoreFailure, Unit>) = apply {
            coEvery {
                conversationGroupRepository.addService(any(), any())
            }.returns(either)
        }

        fun arrange() = this to addService
    }
}

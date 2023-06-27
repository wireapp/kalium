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
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.MLSFailure
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.service.ServiceId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AddServiceToConversationUseCaseTest {

    @Test
    fun givenMemberAndConversation_WhenAddMemberIsSuccessful_ThenReturnSuccess() = runTest {
        val serviceId = ServiceId("serviceId", "provider")
        val (arrangement, addService) = Arrangement()
            .withAddService(Either.Right(Unit))
            .arrange()

        val result = addService(TestConversation.ID, serviceId)

        assertIs<AddServiceToConversationUseCase.Result.Success>(result)

        verify(arrangement.conversationGroupRepository)
            .suspendFunction(arrangement.conversationGroupRepository::addService)
            .with(eq(serviceId), eq(TestConversation.ID))
            .wasInvoked(exactly = once)
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

        verify(arrangement.conversationGroupRepository)
            .suspendFunction(arrangement.conversationGroupRepository::addService)
            .with(eq(serviceId), eq(TestConversation.ID))
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val conversationGroupRepository = mock(ConversationGroupRepository::class)

        private val addService = AddServiceToConversationUseCase(
            conversationGroupRepository
        )

        fun withAddService(either: Either<CoreFailure, Unit>) = apply {
            given(conversationGroupRepository)
                .suspendFunction(conversationGroupRepository::addService)
                .whenInvokedWith(any(), any())
                .thenReturn(either)
        }

        fun arrange() = this to addService
    }
}

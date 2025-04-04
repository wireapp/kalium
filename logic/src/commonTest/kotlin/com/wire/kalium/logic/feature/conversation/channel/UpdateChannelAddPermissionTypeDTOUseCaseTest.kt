/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.conversation.channel

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ConversationDetails.Group.Channel.ChannelAddPermission
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class UpdateChannelAddPermissionTypeDTOUseCaseTest {

    @Test
    fun givenUpdateChannelAddPermissionSucceeds_whenInvokeUseCase_thenReturnSuccess() = runTest {
        val conversationId = ConversationId("value", "domain")
        val (arrangement, usecase) = Arrangement()
            .withUpdateReturning(Either.Right(Unit))
            .arrange()

        val result = usecase(conversationId, ChannelAddPermission.ADMINS)

        assertTrue(result is UpdateChannelAddPermissionUseCase.UpdateChannelAddPermissionUseCaseResult.Success)
        coVerify { arrangement.conversationRepository.updateChannelAddPermission(any(), any()) }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUpdateChannelAddPermissionFailsWhenInvokeUseCase_thenReturnFailure() = runTest {
        val conversationId = ConversationId("value", "domain")
        val (arrangement, usecase) = Arrangement()
            .withUpdateReturning(Either.Left(CoreFailure.MissingClientRegistration))
            .arrange()

        val result = usecase(conversationId, ChannelAddPermission.ADMINS)

        assertTrue(result is UpdateChannelAddPermissionUseCase.UpdateChannelAddPermissionUseCaseResult.Failure)
        coVerify { arrangement.conversationRepository.updateChannelAddPermission(any(), any()) }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        private val updateChannelAddPermission = UpdateChannelAddPermissionUseCaseImpl(conversationRepository)

        suspend fun withUpdateReturning(either: Either<CoreFailure, Unit>) = apply {
            coEvery {
                conversationRepository.updateChannelAddPermission(any(), any())
            }.returns(either)
        }

        fun arrange(block: Arrangement.() -> Unit = { }) = apply(block).let { this to updateChannelAddPermission }
    }
}

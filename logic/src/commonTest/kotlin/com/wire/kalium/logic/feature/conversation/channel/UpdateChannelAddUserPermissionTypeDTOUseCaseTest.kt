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
import com.wire.kalium.logic.data.conversation.ConversationDetails.Group.Channel.ChannelAddUserPermission
import com.wire.kalium.logic.data.conversation.channel.ChannelRepository
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

class UpdateChannelAddUserPermissionTypeDTOUseCaseTest {

    @Test
    fun givenUpdateChannelAddUserPermissionSucceeds_whenInvokeUseCase_thenReturnSuccess() = runTest {
        val conversationId = ConversationId("value", "domain")
        val (arrangement, usecase) = Arrangement()
            .withUpdateReturning(Either.Right(Unit))
            .arrange()

        val result = usecase(conversationId, ChannelAddUserPermission.ADMINS)

        assertTrue(result is UpdateChannelAddUserPermissionUseCase.UpdateChannelAddUserPermissionUseCaseResult.Success)
        coVerify { arrangement.channelRepository.updateAddUserPermission(any(), any()) }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUpdateChannelAddUserPermissionFailsWhenInvokeUseCase_thenReturnFailure() = runTest {
        val conversationId = ConversationId("value", "domain")
        val (arrangement, usecase) = Arrangement()
            .withUpdateReturning(Either.Left(CoreFailure.MissingClientRegistration))
            .arrange()

        val result = usecase(conversationId, ChannelAddUserPermission.ADMINS)

        assertTrue(result is UpdateChannelAddUserPermissionUseCase.UpdateChannelAddUserPermissionUseCaseResult.Failure)
        coVerify { arrangement.channelRepository.updateAddUserPermission(any(), any()) }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val channelRepository = mock(ChannelRepository::class)

        private val updateChannelAddUserPermission = UpdateChannelAddUserPermissionUseCaseImpl(channelRepository)

        suspend fun withUpdateReturning(either: Either<CoreFailure, Unit>) = apply {
            coEvery {
                channelRepository.updateAddUserPermission(any(), any())
            }.returns(either)
        }

        fun arrange(block: Arrangement.() -> Unit = { }) = apply(block).let { this to updateChannelAddUserPermission }
    }
}

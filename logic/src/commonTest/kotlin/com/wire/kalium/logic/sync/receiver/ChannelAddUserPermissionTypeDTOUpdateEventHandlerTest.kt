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
package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.isLeft
import com.wire.kalium.common.functional.isRight
import com.wire.kalium.logic.data.conversation.channel.ChannelRepository
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.sync.receiver.conversation.ChannelAddUserPermissionUpdateEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ChannelAddUserPermissionUpdateEventHandlerImpl
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ChannelAddUserPermissionTypeDTOUpdateEventHandlerTest {

    @Test
    fun givenRepositorySuccess_whenHandlingEvent_thenReturnUnit() = runTest {
        val event = TestEvent.newConversationChannelAddUserPermissionEvent()
        val (arrangement, eventHandler) = Arrangement()
            .withChannelRepositoryReturning(Either.Right(Unit))
            .arrange()

        val result = eventHandler.handle(event)

        coVerify {
            arrangement.channelRepository.updateAddUserPermissionLocally(eq(event.conversationId), eq(event.channelAddUserPermission))
        }.wasInvoked(exactly = once)
        assertTrue { result.isRight() }
    }

    @Test
    fun givenRepositoryFailure_whenHandlingEvent_thenReturnFailure() = runTest {
        val event = TestEvent.newConversationChannelAddUserPermissionEvent()
        val (arrangement, eventHandler) = Arrangement()
            .withChannelRepositoryReturning(Either.Left(CoreFailure.InvalidEventSenderID))
            .arrange()

        val result = eventHandler.handle(event)

        coVerify {
            arrangement.channelRepository.updateAddUserPermissionLocally(eq(event.conversationId), eq(event.channelAddUserPermission))
        }.wasInvoked(exactly = once)
        assertTrue { result.isLeft() }
    }

    private class Arrangement {

        @Mock
        val channelRepository = mock(ChannelRepository::class)

        private val channelAddUserPermissionUpdateEventHandler: ChannelAddUserPermissionUpdateEventHandler by lazy {
            ChannelAddUserPermissionUpdateEventHandlerImpl(channelRepository)
        }

        suspend fun withChannelRepositoryReturning(result: Either<CoreFailure, Unit>) = apply {
            coEvery {
                channelRepository.updateAddUserPermissionLocally(any(), any())
            }.returns(result)
        }

        fun arrange() = this to channelAddUserPermissionUpdateEventHandler
    }
}

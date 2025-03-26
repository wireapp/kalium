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

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.sync.receiver.conversation.ChannelAddPermissionUpdateEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ChannelAddPermissionUpdateEventHandlerImpl
import io.mockative.Mock
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ChannelAddPermissionUpdateEventHandlerTest {

    @Test
    fun givenEvent_whenHandle_thenUpdateChannelAddPermission() = runTest {
        val event = TestEvent.newConversationChannelAddPermissionEvent()
        val (arrangement, eventHandler) = Arrangement().arrange()

        eventHandler.handle(event)

        coVerify {
            arrangement.conversationRepository.updateChannelAddPermissionLocally(eq(event.conversationId), eq(event.channelAddPermission))
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        private val channelAddPermissionUpdateEventHandler: ChannelAddPermissionUpdateEventHandler by lazy {
            ChannelAddPermissionUpdateEventHandlerImpl(conversationRepository)
        }

        fun arrange() = this to channelAddPermissionUpdateEventHandler
    }
}

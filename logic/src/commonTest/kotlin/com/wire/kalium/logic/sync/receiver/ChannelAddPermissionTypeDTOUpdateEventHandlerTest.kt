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
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.sync.receiver.conversation.ChannelAddPermissionUpdateEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ChannelAddPermissionUpdateEventHandlerImpl
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ChannelAddPermissionTypeDTOUpdateEventHandlerTest {

    @Test
    fun givenRepositorySuccess_whenHandlingEvent_thenReturnUnit() = runTest {
        val event = TestEvent.newConversationChannelAddPermissionEvent()
        val (arrangement, eventHandler) = Arrangement()
            .withConversationRepositoryReturning(Either.Right(Unit))
            .arrange()

        val result = eventHandler.handle(event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.updateChannelAddPermissionLocally(eq(event.conversationId), eq(event.channelAddPermission))
        }
        assertTrue { result.isRight() }
    }

    @Test
    fun givenRepositoryFailure_whenHandlingEvent_thenReturnFailure() = runTest {
        val event = TestEvent.newConversationChannelAddPermissionEvent()
        val (arrangement, eventHandler) = Arrangement()
            .withConversationRepositoryReturning(Either.Left(CoreFailure.InvalidEventSenderID))
            .arrange()

        val result = eventHandler.handle(event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.updateChannelAddPermissionLocally(eq(event.conversationId), eq(event.channelAddPermission))
        }
        assertTrue { result.isLeft() }
    }

    private class Arrangement {

        val conversationRepository = mock<ConversationRepository>()

        private val channelAddPermissionUpdateEventHandler: ChannelAddPermissionUpdateEventHandler by lazy {
            ChannelAddPermissionUpdateEventHandlerImpl(conversationRepository)
        }

        suspend fun withConversationRepositoryReturning(result: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                conversationRepository.updateChannelAddPermissionLocally(any(), any())
            } returns result
        }

        fun arrange() = this to channelAddPermissionUpdateEventHandler
    }
}

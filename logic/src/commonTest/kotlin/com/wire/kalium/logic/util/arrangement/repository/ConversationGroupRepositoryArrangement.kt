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
package com.wire.kalium.logic.util.arrangement.repository

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.common.functional.Either
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.fake.valueOf
import io.mockative.matchers.AnyMatcher
import io.mockative.matchers.Matcher
import io.mockative.matches
import io.mockative.mock

interface ConversationGroupRepositoryArrangement {
    val conversationGroupRepository: ConversationGroupRepository

    suspend fun withGenerateGuestRoomLink(
        result: Either<NetworkFailure, EventContentDTO.Conversation.CodeUpdated>,
        conversationId: Matcher<ConversationId> = AnyMatcher(valueOf())
    ) {
        coEvery {
            conversationGroupRepository.generateGuestRoomLink(matches { conversationId.matches(it) }, any())
        }.returns(result)
    }

    suspend fun withCreateGroupConversationReturning(result: Either<CoreFailure, Conversation>) {
        coEvery {
            conversationGroupRepository.createGroupConversation(any(), any(), any())
        }.returns(result)
    }
}

class ConversationGroupRepositoryArrangementImpl : ConversationGroupRepositoryArrangement {
    @Mock
    override val conversationGroupRepository = mock(ConversationGroupRepository::class)
}


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
package com.wire.kalium.logic.util.arrangement.repository

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.given
import io.mockative.matchers.Matcher
import io.mockative.mock

interface ConversationGroupRepositoryArrangement {
    val conversationGroupRepository: ConversationGroupRepository

    fun withGenerateGuestRoomLink(
        result: Either<NetworkFailure, EventContentDTO.Conversation.CodeUpdated>,
        conversationId: Matcher<ConversationId> = any()
    ) {
        given(conversationGroupRepository)
            .suspendFunction(conversationGroupRepository::generateGuestRoomLink)
            .whenInvokedWith(conversationId)
            .thenReturn(result)
    }

    fun withCreateGroupConversationReturning(result: Either<CoreFailure, Conversation>) {
        given(conversationGroupRepository)
            .suspendFunction(conversationGroupRepository::createGroupConversation)
            .whenInvokedWith(anything(), anything(), anything())
            .thenReturn(result)
    }
}

class ConversationGroupRepositoryArrangementImpl : ConversationGroupRepositoryArrangement {
    @Mock
    override val conversationGroupRepository = mock(ConversationGroupRepository::class)
}


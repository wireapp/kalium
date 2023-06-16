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

package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventLoggingStatus
import com.wire.kalium.logic.data.event.logEventProcessing
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.SelfTeamIdProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.getOrNull
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.DateTimeUtil

interface NewConversationEventHandler {
    suspend fun handle(event: Event.Conversation.NewConversation): Either<CoreFailure, Unit>
}

internal class NewConversationEventHandlerImpl(
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val newGroupConversationSystemMessagesCreator: NewGroupConversationSystemMessagesCreator,
) : NewConversationEventHandler {

    override suspend fun handle(event: Event.Conversation.NewConversation): Either<CoreFailure, Unit> = conversationRepository
        .persistConversations(listOf(event.conversation), selfTeamIdProvider().getOrNull()?.value, originatedFromEvent = true)
        .flatMap { conversationRepository.updateConversationModifiedDate(event.conversationId, DateTimeUtil.currentInstant()) }
        .flatMap {
            userRepository.fetchUsersIfUnknownByIds(event.conversation.members.otherMembers.map { it.id.toModel() }.toSet())
        }.onSuccess {
            createSystemMessagesForNewConversation(event)
            kaliumLogger.logEventProcessing(EventLoggingStatus.SUCCESS, event)
        }
        .onFailure {
            kaliumLogger.logEventProcessing(EventLoggingStatus.FAILURE, event, Pair("errorInfo", "$it"))
        }

    /**
     * Creates system messages for new conversation.
     * Conversation started, members added and failed, read receipt status.
     */
    private suspend fun createSystemMessagesForNewConversation(event: Event.Conversation.NewConversation) = run {
        newGroupConversationSystemMessagesCreator.conversationStarted(event.senderUserId, event.conversation)
        newGroupConversationSystemMessagesCreator.conversationResolvedMembersAddedAndFailed(
            event.conversationId.toDao(),
            event.conversation
        )
        newGroupConversationSystemMessagesCreator.conversationReadReceiptStatus(event.conversation)
    }
}

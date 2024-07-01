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

package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.conversation.toConversationType
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.getOrNull
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.createEventProcessingLogger
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.util.DateTimeUtil

interface NewConversationEventHandler {
    suspend fun handle(event: Event.Conversation.NewConversation)
}

internal class NewConversationEventHandlerImpl(
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val newGroupConversationSystemMessagesCreator: NewGroupConversationSystemMessagesCreator,
    private val oneOnOneResolver: OneOnOneResolver,
) : NewConversationEventHandler {

    override suspend fun handle(event: Event.Conversation.NewConversation) {
        val eventLogger = kaliumLogger.createEventProcessingLogger(event)
        val selfUserTeamId = selfTeamIdProvider().getOrNull()
        conversationRepository
            .persistConversation(event.conversation, selfUserTeamId?.value, true)
            .flatMap { isNewUnhandledConversation ->
                resolveConversationIfOneOnOne(selfUserTeamId, event)
                    .flatMap {
                        conversationRepository.updateConversationModifiedDate(event.conversationId, DateTimeUtil.currentInstant())
                    }
                    .flatMap {
                        userRepository.fetchUsersIfUnknownByIds(event.conversation.members.otherMembers.map { it.id.toModel() }.toSet())
                    }
                    .map { isNewUnhandledConversation }
            }.onSuccess { isNewUnhandledConversation ->
                createSystemMessagesForNewConversation(isNewUnhandledConversation, event)
                eventLogger.logSuccess()
            }.onFailure {
                eventLogger.logFailure(it)
            }
    }

    private suspend fun resolveConversationIfOneOnOne(selfUserTeamId: TeamId?, event: Event.Conversation.NewConversation) =
        if (event.conversation.toConversationType(selfUserTeamId) == ConversationEntity.Type.ONE_ON_ONE) {
            val otherUserId = event.conversation.members.otherMembers.first().id.toModel()
            oneOnOneResolver.resolveOneOnOneConversationWithUserId(
                userId = otherUserId,
                invalidateCurrentKnownProtocols = true
            ).map { Unit }
        } else Either.Right(Unit)

    /**
     * Creates system messages for new conversation.
     * Conversation started, members added and failed, read receipt status.
     *
     * @param isNewUnhandledConversation if true we need to generate system messages for new conversation
     * @param event new conversation event
     */
    private suspend fun createSystemMessagesForNewConversation(
        isNewUnhandledConversation: Boolean,
        event: Event.Conversation.NewConversation
    ) {
        if (isNewUnhandledConversation) {
            newGroupConversationSystemMessagesCreator.conversationStarted(event.senderUserId, event.conversation, event.dateTime)
            newGroupConversationSystemMessagesCreator.conversationResolvedMembersAdded(
                event.conversationId.toDao(),
                event.conversation.members.otherMembers.map { it.id.toModel() },
                event.dateTime
            )
            newGroupConversationSystemMessagesCreator.conversationReadReceiptStatus(event.conversation, event.dateTime)
            newGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning(
                event.conversation.id.toModel(),
                event.dateTime
            )
        }
    }
}

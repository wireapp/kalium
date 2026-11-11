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

import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.ConversationSyncReason
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.conversation.PersistConversationUseCase
import com.wire.kalium.logic.data.conversation.createForEvent
import com.wire.kalium.logic.data.conversation.mls.PendingActionsRepository
import com.wire.kalium.logic.data.conversation.toConversationType
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.util.createEventProcessingLogger
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.util.DateTimeUtil

internal interface NewConversationEventHandler {
    suspend fun handle(transactionContext: CryptoTransactionContext, event: Event.Conversation.NewConversation)
}

@Suppress("LongParameterList")
internal class NewConversationEventHandlerImpl(
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val newGroupConversationSystemMessagesCreator: NewGroupConversationSystemMessagesCreator,
    private val pendingActionsRepository: PendingActionsRepository,
    private val resolvePendingOneOnOnes: () -> Unit,
    private val persistConversation: PersistConversationUseCase,
) : NewConversationEventHandler {

    override suspend fun handle(transactionContext: CryptoTransactionContext, event: Event.Conversation.NewConversation) {
        val eventLogger = kaliumLogger.createEventProcessingLogger(event)
        val selfUserTeamId = selfTeamIdProvider().getOrNull()
        persistConversation(transactionContext, event.conversation, reason = ConversationSyncReason.Event)
            .flatMap { isNewUnhandledConversation ->
                queueResolutionIfOneOnOne(selfUserTeamId, event)
                conversationRepository.updateConversationModifiedDate(event.conversationId, DateTimeUtil.currentInstant())
                    .flatMap {
                        userRepository.fetchUsersIfUnknownByIds(
                            event.conversation.members.otherMembers.map {
                                it.id.toModel()
                            }.toSet()
                        )
                    }
                    .map { isNewUnhandledConversation }
            }.onSuccess { isNewUnhandledConversation ->
                if (isNewUnhandledConversation) newGroupConversationSystemMessagesCreator.createForEvent(event, selfUserTeamId)
                eventLogger.logSuccess()
            }.onFailure {
                eventLogger.logFailure(it)
            }
    }

    // Resolving can call the backend and join or establish MLS groups, so it must not run inside the receive batch.
    // The queue row outlives a failed batch, and the session-owned drain resolves it once sync is live.
    private suspend fun queueResolutionIfOneOnOne(selfUserTeamId: TeamId?, event: Event.Conversation.NewConversation) {
        if (event.conversation.toConversationType(selfUserTeamId) != ConversationEntity.Type.ONE_ON_ONE) return
        val otherUserId = event.conversation.members.otherMembers.firstOrNull()?.id?.toModel() ?: return
        pendingActionsRepository.enqueuePendingOneOnOneResolution(otherUserId)
        resolvePendingOneOnOnes()
    }
}

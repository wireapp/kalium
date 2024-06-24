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

import com.benasher44.uuid.uuid4
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.util.createEventProcessingLogger
import com.wire.kalium.util.serialization.toJsonElement

interface MemberJoinEventHandler {
    suspend fun handle(event: Event.Conversation.MemberJoin): Either<CoreFailure, Unit>
}

internal class MemberJoinEventHandlerImpl(
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val persistMessage: PersistMessageUseCase,
    private val legalHoldHandler: LegalHoldHandler
) : MemberJoinEventHandler {
    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER) }

    override suspend fun handle(event: Event.Conversation.MemberJoin): Either<CoreFailure, Unit> {
        val eventLogger = logger.createEventProcessingLogger(event)
        // the group info need to be fetched for the following cases:
        // 1. self user is added/re-added to a group and we need to update the group info in case something changed form last time
        // 2. the new member is a bot in that case we need to make the group a bot 1:1
        // 3. fetch group info in case it is not stored in the first place
        return conversationRepository.fetchConversation(event.conversationId)
            .run {
                onSuccess {
                    val logMap = mapOf(
                        "event" to event.toLogMap()
                    )
                    logger.v("Success fetching conversation details on MemberJoin Event: ${logMap.toJsonElement()}")
                }
                onFailure {
                    val logMap = mapOf(
                        "event" to event.toLogMap(),
                        "errorInfo" to "$it"
                    )
                    logger.w("Failure fetching conversation details on MemberJoin Event: ${logMap.toJsonElement()}")
                }
                // Even if unable to fetch conversation details, at least attempt adding the members
                userRepository.fetchUsersIfUnknownByIds(event.members.map { it.id }.toSet())
                conversationRepository.persistMembers(event.members, event.conversationId)
            }.onSuccess {
                conversationRepository.detailsById(event.conversationId).onSuccess { conversation ->
                    if (conversation.type == Conversation.Type.GROUP) addSystemMessage(event)
                }
                legalHoldHandler.handleConversationMembersChanged(event.conversationId)
                eventLogger.logSuccess()
            }.onFailure {
                eventLogger.logFailure(it)
            }
    }

    private suspend fun addSystemMessage(event: Event.Conversation.MemberJoin) {
        val message = Message.System(
            id = event.id.ifEmpty { uuid4().toString() },
            content = MessageContent.MemberChange.Added(members = event.members.map { it.id }),
            conversationId = event.conversationId,
            date = event.timestampIso,
            senderUserId = event.addedBy,
            status = Message.Status.Sent,
            visibility = Message.Visibility.VISIBLE,
            expirationData = null
        )
        persistMessage(message)
    }
}

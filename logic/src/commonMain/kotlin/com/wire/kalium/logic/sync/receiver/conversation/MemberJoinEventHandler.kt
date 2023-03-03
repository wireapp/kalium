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

import com.benasher44.uuid.uuid4
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreFailure
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

interface MemberJoinEventHandler {
    suspend fun handle(event: Event.Conversation.MemberJoin): Either<CoreFailure, Unit>
}

internal class MemberJoinEventHandlerImpl(
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val persistMessage: PersistMessageUseCase
) : MemberJoinEventHandler {
    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER) }

    override suspend fun handle(event: Event.Conversation.MemberJoin) =
        // Attempt to fetch conversation details if needed, as this might be an unknown conversation
        conversationRepository.fetchConversationIfUnknown(event.conversationId)
            .run {
                onSuccess {
                    logger.v("Succeeded fetching conversation details on MemberJoin Event: ${event.toLogString()}")
                }
                onFailure {
                    logger.w("Failure fetching conversation details on MemberJoin Event: ${event.toLogString()}")
                }
                // Even if unable to fetch conversation details, at least attempt adding the members
                userRepository.fetchUsersIfUnknownByIds(event.members.map { it.id }.toSet())
                conversationRepository.persistMembers(event.members, event.conversationId)
            }.onSuccess {
                val message = Message.System(
                    id = event.id.ifEmpty { uuid4().toString() },
                    content = MessageContent.MemberChange.Added(members = event.members.map { it.id }),
                    conversationId = event.conversationId,
                    date = event.timestampIso,
                    senderUserId = event.addedBy,
                    status = Message.Status.SENT,
                    visibility = Message.Visibility.VISIBLE
                )
                persistMessage(message)

            }.onFailure { logger.e("failure on member join event: $it") }
}

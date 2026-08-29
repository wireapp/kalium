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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.MLSResetEventRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.MemberLeaveReason
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.mls.ConversationProtocolGetter
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.util.createEventProcessingLogger
import com.wire.kalium.logic.util.wrapInMLSContext

public interface MemberLeaveEventHandler {
    public suspend fun handle(
        transactionContext: CryptoTransactionContext,
        event: Event.Conversation.MemberLeave,
    ): Either<CoreFailure, Unit>
}

@Suppress("LongParameterList")
public class MemberLeaveEventHandlerImpl public constructor(
    private val conversationLifecycleEventRepository: ConversationLifecycleEventRepository,
    private val userRepository: MemberLeaveEventUserRepository,
    private val conversationRepository: ConversationProtocolGetter,
    private val persistMessage: PersistMessageUseCase,
    private val updateConversationClientsForCurrentCall: suspend (ConversationId) -> Unit,
    private val handleConversationMembersChanged: suspend (ConversationId) -> Either<CoreFailure, Unit>,
    private val selfTeamId: suspend () -> Either<CoreFailure, TeamId?>,
    private val mlsConversationRepository: MLSResetEventRepository,
    private val deleteMeetingsByConversationId: suspend (ConversationId) -> Either<CoreFailure, Unit>,
    private val selfUserId: UserId,
) : MemberLeaveEventHandler {

    override suspend fun handle(
        transactionContext: CryptoTransactionContext,
        event: Event.Conversation.MemberLeave,
    ): Either<CoreFailure, Unit> {
        val eventLogger = kaliumLogger.createEventProcessingLogger(event)
        if (event.reason == MemberLeaveReason.UserDeleted) {
            userRepository.markAsDeleted(event.removedList)
        }
        return deleteMembers(event.removedList, event.conversationId)
            .onSuccess {
                updateConversationClientsForCurrentCall(event.conversationId)
            }
            .onSuccess {
                wipeMLSConversationIfNeeded(transactionContext, event)
            }
            .onSuccess {
                // fetch required unknown users that haven't been persisted during slow sync, e.g. from another team
                // and keep them to properly show this member-leave message
                userRepository.fetchUsersIfUnknownByIds(event.removedList.toSet())
            }.flatMap { numberOfUsersDeleted ->

                if (numberOfUsersDeleted <= 0) {
                    return@flatMap Either.Right(Unit)
                }

                resolveMessageContent(event).let { content ->
                    Message.System(
                        id = event.id,
                        content = content,
                        conversationId = event.conversationId,
                        date = event.dateTime,
                        senderUserId = event.removedBy,
                        status = Message.Status.Sent,
                        visibility = Message.Visibility.VISIBLE,
                        expirationData = null
                    ).let {
                        persistMessage(it)
                        Either.Right(Unit)
                    }
                }
                handleConversationMembersChanged(event.conversationId)
            }
            .flatMap {
                deleteMeetingsIfNeeded(event)
            }
            .onSuccess {
                eventLogger.logSuccess()
            }.onFailure {
                eventLogger.logFailure(it)
            }
    }

    private suspend fun resolveMessageContent(event: Event.Conversation.MemberLeave): MessageContent.System {
        return when (event.reason) {
            MemberLeaveReason.Left,
            MemberLeaveReason.Removed -> MessageContent.MemberChange.Removed(members = event.removedList)

            MemberLeaveReason.UserDeleted -> handleUserDeleted(event)
        }
    }

    private suspend fun handleUserDeleted(event: Event.Conversation.MemberLeave): MessageContent.System {
        val teamId = selfTeamId().getOrNull()

        return when {
            teamId == null -> MessageContent.MemberChange.Removed(members = event.removedList)
            userRepository.isAtLeastOneUserATeamMember(
                event.removedList,
                teamId
            ).getOrElse(false) -> MessageContent.MemberChange.RemovedFromTeam(members = event.removedList)

            else -> MessageContent.MemberChange.Removed(members = event.removedList)
        }
    }

    private suspend fun wipeMLSConversationIfNeeded(transactionContext: CryptoTransactionContext, event: Event.Conversation.MemberLeave) {
        val isSelfUserNoLongerMember = selfUserId in event.removedList
        if (!isSelfUserNoLongerMember) return

        conversationRepository.getConversationProtocolInfo(event.conversationId).flatMap { protocolInfo ->
            if (protocolInfo is Conversation.ProtocolInfo.MLSCapable) {
                transactionContext.wrapInMLSContext {
                    mlsConversationRepository.leaveGroup(it, protocolInfo.groupId)
                }.map { Unit }
            } else {
                Either.Right(Unit)
            }
        }

    }

    private suspend fun deleteMembers(
        userIDList: List<UserId>,
        conversationID: ConversationId
    ): Either<CoreFailure, Long> = conversationLifecycleEventRepository.deleteMembers(userIDList, conversationID)

    private suspend fun deleteMeetingsIfNeeded(event: Event.Conversation.MemberLeave): Either<CoreFailure, Unit> =
        when (selfUserId in event.removedList) {
            true -> deleteMeetingsByConversationId(event.conversationId)
            false -> Either.Right(Unit)
        }
}

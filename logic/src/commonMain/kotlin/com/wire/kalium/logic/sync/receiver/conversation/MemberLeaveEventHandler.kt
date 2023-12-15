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
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventLoggingStatus
import com.wire.kalium.logic.data.event.MemberLeaveReason
import com.wire.kalium.logic.data.event.logEventProcessing
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.call.usecase.UpdateConversationClientsForCurrentCallUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.functional.getOrNull
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.dao.member.MemberDAO

interface MemberLeaveEventHandler {
    suspend fun handle(event: Event.Conversation.MemberLeave): Either<CoreFailure, Unit>
}

internal class MemberLeaveEventHandlerImpl(
    private val memberDAO: MemberDAO,
    private val userRepository: UserRepository,
    private val persistMessage: PersistMessageUseCase,
    private val updateConversationClientsForCurrentCall: Lazy<UpdateConversationClientsForCurrentCallUseCase>,
    private val selfTeamIdProvider: SelfTeamIdProvider
) : MemberLeaveEventHandler {

    override suspend fun handle(event: Event.Conversation.MemberLeave) =
        let {
            when (event.reason) {
                MemberLeaveReason.Removed,
                MemberLeaveReason.Left -> {
                    deleteMembers(event.removedList, event.conversationId)
                }

                MemberLeaveReason.UserDeleted -> {
                    userRepository.markUserAsDeletedAndRemoveFromGroupConversations(event.removedList)
                }
            }
        }.onSuccess {
            updateConversationClientsForCurrentCall.value(event.conversationId)
        }.onSuccess {
            // fetch required unknown users that haven't been persisted during slow sync, e.g. from another team
            // and keep them to properly show this member-leave message
            userRepository.fetchUsersIfUnknownByIds(event.removedList.toSet())
        }.onSuccess {
            val content: MessageContent.System? = when (event.reason) {
                MemberLeaveReason.Left,
                MemberLeaveReason.Removed -> MessageContent.MemberChange.Removed(members = event.removedList)

                MemberLeaveReason.UserDeleted -> {
                    selfTeamIdProvider().getOrNull()?.let { teamId ->
                        userRepository.isAtLeastOneUserATeamMember(event.removedList, teamId).getOrElse(false).let {
                            if (it) {
                                MessageContent.MemberChange.RemovedFromTeam(
                                    members = event.removedList
                                )
                            } else {
                                null
                            }
                        }
                    }
                }
            }

            content?.let {
                Message.System(
                    id = event.id,
                    content = it,
                    conversationId = event.conversationId,
                    date = event.timestampIso,
                    senderUserId = event.removedBy,
                    status = Message.Status.Sent,
                    visibility = Message.Visibility.VISIBLE,
                    expirationData = null
                ).also {
                    persistMessage(it)
                }
            }
        }.onSuccess {
            kaliumLogger
                .logEventProcessing(
                    EventLoggingStatus.SUCCESS,
                    event
                )
        }.onFailure {
            kaliumLogger
                .logEventProcessing(
                    EventLoggingStatus.FAILURE,
                    event,
                    Pair("errorInfo", "$it")
                )
        }

    private suspend fun deleteMembers(
        userIDList: List<UserId>,
        conversationID: ConversationId
    ): Either<CoreFailure, Unit> =
        wrapStorageRequest {
            memberDAO.deleteMembersByQualifiedID(
                userIDList.map { it.toDao() },
                conversationID.toDao()
            )
        }
}

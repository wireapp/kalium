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

package com.wire.kalium.logic.sync.receiver

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventLoggingStatus
import com.wire.kalium.logic.data.event.logEventProcessing
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.first

interface TeamEventReceiver : EventReceiver<Event.Team>

internal class TeamEventReceiverImpl(
    private val teamRepository: TeamRepository,
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val persistMessage: PersistMessageUseCase,
    private val selfUserId: UserId,
) : TeamEventReceiver {

    override suspend fun onEvent(event: Event.Team): Either<CoreFailure, Unit> {
        when (event) {
            is Event.Team.MemberJoin -> handleMemberJoin(event)
            is Event.Team.MemberLeave -> handleMemberLeave(event)
            is Event.Team.MemberUpdate -> handleMemberUpdate(event)
            is Event.Team.Update -> handleUpdate(event)
        }
        // TODO: Make sure errors are accounted for by each handler.
        //       onEvent now requires Either, so we can propagate errors,
        //       but not all handlers are using it yet.
        //       Returning Either.Right is the equivalent of how it was originally working.
        return Either.Right(Unit)
    }

    private suspend fun handleMemberJoin(event: Event.Team.MemberJoin) =
        teamRepository.fetchTeamMember(
            teamId = event.teamId,
            userId = event.memberId,
        )
            .onSuccess {
                kaliumLogger
                    .logEventProcessing(
                        EventLoggingStatus.SUCCESS,
                        event
                    )
            }
            .onFailure {
                kaliumLogger
                    .logEventProcessing(
                        EventLoggingStatus.FAILURE,
                        event,
                        Pair("errorInfo", "$it")
                    )
            }

    @Suppress("LongMethod")
    private suspend fun handleMemberLeave(event: Event.Team.MemberLeave) {
        val userId = UserId(event.memberId, selfUserId.domain)
        teamRepository.removeTeamMember(
            teamId = event.teamId,
            userId = event.memberId,
        )
            .onSuccess {
                val knownUser = userRepository.getKnownUser(userId).first()
                if (knownUser?.name != null) {
                    conversationRepository.getConversationIdsByUserId(userId)
                        .onSuccess {
                            it.forEach { conversationId ->
                                val message = Message.System(
                                    id = uuid4().toString(), // We generate a random uuid for this new system message
                                    content = MessageContent.TeamMemberRemoved(knownUser.name),
                                    conversationId = conversationId,
                                    date = event.timestampIso,
                                    senderUserId = userId,
                                    status = Message.Status.Sent,
                                    visibility = Message.Visibility.VISIBLE
                                )
                                persistMessage(message)
                            }

                            conversationRepository.deleteUserFromConversations(userId)
                                .onSuccess {
                                    kaliumLogger
                                        .logEventProcessing(
                                            EventLoggingStatus.SUCCESS,
                                            event
                                        )
                                }
                                .onFailure { deleteFailure ->
                                    kaliumLogger
                                        .logEventProcessing(
                                            EventLoggingStatus.FAILURE,
                                            event,
                                            Pair("errorInfo", "$deleteFailure")
                                        )
                                }

                        }.onFailure {
                            kaliumLogger
                                .logEventProcessing(
                                    EventLoggingStatus.FAILURE,
                                    event,
                                    Pair("errorInfo", "$it")
                                )
                        }
                } else {
                    kaliumLogger
                        .logEventProcessing(
                            EventLoggingStatus.SKIPPED,
                            event,
                            Pair("info", "User or User name is null")
                        )
                }
            }
            .onFailure {
                kaliumLogger
                    .logEventProcessing(
                        EventLoggingStatus.FAILURE,
                        event,
                        Pair("errorInfo", "$it")
                    )
            }
    }

    private suspend fun handleMemberUpdate(event: Event.Team.MemberUpdate) =
        teamRepository.updateMemberRole(
            teamId = event.teamId,
            userId = event.memberId,
            permissionCode = event.permissionCode,
        )
            .onSuccess {
                kaliumLogger
                    .logEventProcessing(
                        EventLoggingStatus.SUCCESS,
                        event
                    )
            }
            .onFailure {
                kaliumLogger
                    .logEventProcessing(
                        EventLoggingStatus.FAILURE,
                        event,
                        Pair("errorInfo", "$it")
                    )
            }

    private suspend fun handleUpdate(event: Event.Team.Update) =
        teamRepository.updateTeam(
            Team(
                id = event.teamId,
                name = event.name,
                icon = event.icon
            )
        )
            .onSuccess {
                kaliumLogger
                    .logEventProcessing(
                        EventLoggingStatus.SUCCESS,
                        event
                    )
            }
            .onFailure {
                kaliumLogger
                    .logEventProcessing(
                        EventLoggingStatus.FAILURE,
                        event,
                        Pair("errorInfo", "$it")
                    )
            }
}

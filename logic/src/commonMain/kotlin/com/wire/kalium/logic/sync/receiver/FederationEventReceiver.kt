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
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.conversation.RemoveMemberFromConversationUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.dao.member.MemberDAO
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

interface FederationEventReceiver : EventReceiver<Event.Federation>

class FederationEventReceiverImpl internal constructor(
    private val conversationRepository: ConversationRepository,
    private val memberDAO: MemberDAO,
    private val persistMessage: PersistMessageUseCase,
    private val selfUserId: UserId,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : FederationEventReceiver {

    override suspend fun onEvent(event: Event.Federation): Either<CoreFailure, Unit> {
        when (event) {
            is Event.Federation.Delete -> handleDeleteEvent(event)
            is Event.Federation.ConnectionRemoved -> handleConnectionRemovedEvent(event)
        }
        return Either.Right(Unit)
    }

    private suspend fun handleDeleteEvent(event: Event.Federation.Delete) = withContext(dispatchers.io) {
        conversationRepository.getConversationWithMembersWithBothDomains(event.domain, selfUserId.domain)
            .onSuccess { conversationIdWithUserIdList ->
                conversationIdWithUserIdList.forEach { (conversationId, userIds) ->
                    when (conversationId.domain) {
                        selfUserId.domain -> {
                            removeMembersFromConversation(conversationId, userIds.filterNot { it.domain == selfUserId.domain })
                            // TODO check how 1on1 conversations behave
                        }

                        event.domain -> {
                            removeMembersFromConversation(conversationId, userIds.filter { it.domain == selfUserId.domain })
                            // TODO check how 1on1 conversations behave
                        }

                        else -> removeMembersFromConversation(conversationId, userIds)

                    }
                }
            }
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
        // TODO remove other domain from one1one conversations

        // TODO remove connection requests with domain

    }

    // TODO KBX handle all cases
    private suspend fun handleConnectionRemovedEvent(event: Event.Federation.ConnectionRemoved) =
        withContext(dispatchers.io) {
            val firstDomain = event.domains.first()
            val secondDomain = event.domains.last()

            conversationRepository.getConversationWithMembersWithBothDomains(firstDomain, secondDomain)
                .onSuccess { conversationIdWithUserIdList ->
                    conversationIdWithUserIdList.forEach { (conversationId, userIds) ->
                        when (conversationId.domain) {
                            firstDomain -> {
                                removeMembersFromConversation(conversationId, userIds.filterNot { it.domain == firstDomain })
                            }

                            secondDomain -> {
                                removeMembersFromConversation(conversationId, userIds.filterNot { it.domain == secondDomain })
                            }

                            else -> removeMembersFromConversation(conversationId, userIds)

                        }
                    }
                }

            conversationRepository.getConversationIdsByDomain(selfUserId.domain).map { conversationIds ->
                conversationIds.map { conversationId ->
                    event.domains.map {
                        conversationRepository.getMemberIdsByTheSameDomainInConversation(it, conversationId)
                            .onSuccess { userIds ->
                                // TODO
                            }
                    }
                }

            }
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


    private suspend fun removeMembersFromConversation(conversationID: ConversationId, userIDList: List<UserId>) {
        deleteMembers(userIDList, conversationID)
            .onSuccess {
                handleMemberRemovedEvent(conversationID, userIDList)
            }
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

    private suspend fun handleMemberRemovedEvent(conversationID: ConversationId, userIDList: List<UserId>) {
        val message = Message.System(
            id = uuid4().toString(),
            content = MessageContent.MemberChange.FederationRemoved(members = userIDList),
            conversationId = conversationID,
            date = DateTimeUtil.currentIsoDateTimeString(),
            senderUserId = selfUserId,
            status = Message.Status.SENT,
            visibility = Message.Visibility.VISIBLE,
            expirationData = null
        )
        persistMessage(message)
    }

}

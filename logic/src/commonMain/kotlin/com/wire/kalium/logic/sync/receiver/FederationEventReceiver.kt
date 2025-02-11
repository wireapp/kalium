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

package com.wire.kalium.logic.sync.receiver

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventDeliveryInfo
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.createEventProcessingLogger
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.dao.member.MemberDAO
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

internal interface FederationEventReceiver : EventReceiver<Event.Federation>

@Suppress("LongParameterList")
class FederationEventReceiverImpl internal constructor(
    private val conversationRepository: ConversationRepository,
    private val connectionRepository: ConnectionRepository,
    private val userRepository: UserRepository,
    private val memberDAO: MemberDAO,
    private val persistMessage: PersistMessageUseCase,
    private val selfUserId: UserId,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : FederationEventReceiver {

    override suspend fun onEvent(event: Event.Federation, deliveryInfo: EventDeliveryInfo): Either<CoreFailure, Unit> {
        when (event) {
            is Event.Federation.Delete -> handleDeleteEvent(event)
            is Event.Federation.ConnectionRemoved -> handleConnectionRemovedEvent(event)
        }
        return Either.Right(Unit)
    }

    private suspend fun handleDeleteEvent(event: Event.Federation.Delete) = withContext(dispatchers.io) {
        val eventLogger = kaliumLogger.createEventProcessingLogger(event)
        // remove pending and sent connections from federated users
        connectionRepository.getConnections()
            .map { it.firstOrNull() }
            .onSuccess { conversationDetailsList ->
                conversationDetailsList?.forEach { conversationDetails ->
                    if (conversationDetails is ConversationDetails.Connection
                        && conversationDetails.otherUser?.id?.domain == event.domain
                    ) {
                        connectionRepository.deleteConnection(conversationDetails.connection)
                    }
                }
            }

        conversationRepository.getOneOnOneConversationsWithFederatedMembers(event.domain)
            .onSuccess { conversationsWithMembers ->
                // mark users as defederated to hold conversation history in oneOnOne conversations
                conversationsWithMembers.forEach { (conversationId, userId) ->
                    handleFederationDeleteEvent(conversationId, event.domain)
                    userRepository.defederateUser(userId)
                }
            }

        conversationRepository.getGroupConversationsWithMembersWithBothDomains(event.domain, selfUserId.domain)
            .onSuccess { conversationsWithMembers ->
                conversationsWithMembers.forEach { (conversationId, userIds) ->
                    handleFederationDeleteEvent(conversationId, event.domain)
                    when (conversationId.domain) {
                        // remove defederated users from self domain conversations
                        selfUserId.domain ->
                            removeMembersFromConversation(conversationId, userIds.filterNot { it.domain == selfUserId.domain })

                        // remove self domain users from defederated domain conversations
                        event.domain ->
                            removeMembersFromConversation(conversationId, userIds.filter { it.domain == selfUserId.domain })

                        // remove self domain and defederated domain users from not defederated and self domain conversations
                        else -> removeMembersFromConversation(conversationId, userIds)
                    }
                }
            }
            .onSuccess {
                eventLogger.logSuccess()
            }
            .onFailure(eventLogger::logFailure)
    }

    private suspend fun handleConnectionRemovedEvent(event: Event.Federation.ConnectionRemoved) {
        val eventLogger = kaliumLogger.createEventProcessingLogger(event)
        if (event.domains.size != EXPECTED_DOMAIN_LIST_SIZE) {
            eventLogger.logFailure(
                extraInfo = arrayOf(
                    "errorInfo" to "Expected $EXPECTED_DOMAIN_LIST_SIZE domains, got ${event.domains.size}"
                )
            )
            return
        }
        val firstDomain = event.domains.first()
        val secondDomain = event.domains.last()

        conversationRepository.getGroupConversationsWithMembersWithBothDomains(firstDomain, secondDomain)
            .onSuccess { conversationsWithMembers ->
                conversationsWithMembers.forEach { (conversationId, userIds) ->
                    handleFederationConnectionRemovedEvent(conversationId, event.domains)
                    when (conversationId.domain) {
                        // remove secondDomain users from firstDomain conversation
                        firstDomain ->
                            removeMembersFromConversation(conversationId, userIds.filter { it.domain == secondDomain })

                        // remove firstDomain users from secondDomain conversation
                        secondDomain ->
                            removeMembersFromConversation(conversationId, userIds.filter { it.domain == firstDomain })

                        // remove firstDomain and secondDomain users from rest conversations
                        else -> removeMembersFromConversation(conversationId, userIds)
                    }
                }
            }
            .onSuccess {
                eventLogger.logSuccess()
            }
            .onFailure(eventLogger::logFailure)
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
        }.map { }

    private suspend fun handleMemberRemovedEvent(conversationID: ConversationId, userIDList: List<UserId>) {
        val message = Message.System(
            id = uuid4().toString(),
            content = MessageContent.MemberChange.FederationRemoved(members = userIDList),
            conversationId = conversationID,
            date = Clock.System.now(),
            senderUserId = selfUserId,
            status = Message.Status.Read(0),
            visibility = Message.Visibility.VISIBLE,
            expirationData = null
        )
        persistMessage(message)
    }

    private suspend fun handleFederationDeleteEvent(conversationID: ConversationId, domain: String) {
        val message = Message.System(
            id = uuid4().toString(),
            content = MessageContent.FederationStopped.Removed(domain),
            conversationId = conversationID,
            date = Clock.System.now(),
            senderUserId = selfUserId,
            status = Message.Status.Read(0),
            visibility = Message.Visibility.VISIBLE,
            expirationData = null
        )
        persistMessage(message)
    }

    private suspend fun handleFederationConnectionRemovedEvent(conversationID: ConversationId, domainList: List<String>) {
        val message = Message.System(
            id = uuid4().toString(),
            content = MessageContent.FederationStopped.ConnectionRemoved(domainList),
            conversationId = conversationID,
            date = Clock.System.now(),
            senderUserId = selfUserId,
            status = Message.Status.Read(0),
            visibility = Message.Visibility.VISIBLE,
            expirationData = null
        )
        persistMessage(message)
    }

    companion object {
        const val EXPECTED_DOMAIN_LIST_SIZE = 2
    }

}

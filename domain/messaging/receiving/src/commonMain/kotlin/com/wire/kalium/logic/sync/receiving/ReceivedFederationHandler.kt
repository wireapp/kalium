/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.membersToRemove
import com.wire.kalium.logic.data.event.federationSystemMessage
import com.wire.kalium.logic.data.event.stoppedContent
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.matchesSystemMessage
import com.wire.kalium.logic.data.message.toEntity
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.db.MessageEffectDAOs
import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.flow.first

internal class ReceivedFederationHandler(
    private val data: MessageEffectDAOs,
    private val self: UserId,
    private val persistMessage: PersistMessageUseCase,
    private val deleteFile: (String) -> Unit,
) {
    /** Retain canonical per-payload messages before an earlier clear removes them during replay. */
    suspend fun previousMessages(event: Event.Federation): List<MessageEntity> =
        listOf(event.id, "${event.id}:members").flatMap { id ->
            data.messages.getConversationIdsWithMessage(id).map { checkNotNull(data.messages.getMessageById(id, it)) }
        }

    suspend fun handle(
        event: Event.Federation,
        previous: List<MessageEntity>,
        requiredEarlier: Set<QualifiedIDEntity>,
        recreatedLater: Set<QualifiedIDEntity>,
    ) {
        if (event is Event.Federation.ConnectionRemoved && event.domains.size != DOMAIN_COUNT) return
        val (first, second) = when (event) {
            is Event.Federation.Delete -> self.domain to event.domain
            is Event.Federation.ConnectionRemoved -> event.domains.first() to event.domains.last()
        }
        val stopped = previous.filter { it.id == event.id }.associateBy { it.conversationId }
        val removed = previous.filter { it.id != event.id }.associateBy { it.conversationId }
        previous.forEach { message ->
            val content = if (message.id == event.id) event.stoppedContent() else {
                val change = message.content as? MessageEntityContent.MemberChange
                MessageContent.MemberChange.FederationRemoved(change?.memberUserIdList.orEmpty().map { it.toModel() })
            }
            val expected = federationSystemMessage(message.id, message.conversationId.toModel(), self, content, message.date)
            check(message.matchesSystemMessage(self.toDao(), expected.toEntity().content)) {
                "Federation message identity conflicts with stored state"
            }
        }
        if (event is Event.Federation.Delete) {
            deleteConnections(event.domain, requiredEarlier, recreatedLater)
            defederatePeers(event, stopped)
        }
        val groups = data.members.getGroupConversationWithUserIdsWithBothDomains(first, second).mapValues { (id, members) ->
            event.membersToRemove(id.domain, self.domain, members.map { it.toModel() }).map { it.toDao() }
        }.toMutableMap()
        removed.forEach { (id, message) ->
            val members = (message.content as? MessageEntityContent.MemberChange)?.memberUserIdList.orEmpty()
            groups[id] = (groups[id].orEmpty() + members).distinct()
        }
        groups.forEach { (id, members) ->
            persist(event.id, id, event.stoppedContent(), stopped[id])
            data.members.deleteMembersByQualifiedID(members, id)
            val canonicalMembers = (removed[id]?.content as? MessageEntityContent.MemberChange)?.memberUserIdList ?: members
            persist(
                "${event.id}:members",
                id,
                MessageContent.MemberChange.FederationRemoved(canonicalMembers.map { it.toModel() }),
                removed[id]
            )
        }
    }

    private suspend fun defederatePeers(event: Event.Federation.Delete, stopped: Map<QualifiedIDEntity, MessageEntity>) {
        val peers = data.members.getOneOneConversationWithFederatedMembers(event.domain).toMutableMap()
        // A prior SQL commit already set defederated. The retained one-to-one members
        // identify the peer whose required state must be repeated on this payload's replay.
        stopped.keys.forEach { id ->
            if (data.conversations.getConversationById(id)?.type == ConversationEntity.Type.ONE_ON_ONE) {
                data.members.getConversationMembers(id).firstOrNull { it.domain == event.domain }?.let { peers[id] = it }
            }
        }
        peers.forEach { (id, peer) ->
            persist(event.id, id, event.stoppedContent(), stopped[id])
            data.users.markUserAsDefederated(peer)
        }
    }

    private suspend fun deleteConnections(domain: String, requiredEarlier: Set<QualifiedIDEntity>, recreatedLater: Set<QualifiedIDEntity>) {
        data.connections.getConnectionRequests().first().filter { it.otherUser?.id?.domain == domain }.forEach { connection ->
            val id = connection.qualifiedConversationId
            check(id !in requiredEarlier && id !in recreatedLater) {
                "Connection deletion requires replayable conversation metadata"
            }
            data.assets.deleteConversationFiles(id, deleteFile)
            data.connections.deleteConnectionDataAndConversation(id)
            data.users.upsertConnectionStatuses(mapOf(connection.qualifiedToId to ConnectionEntity.State.CANCELLED))
        }
    }

    private suspend fun persist(id: String, conversation: QualifiedIDEntity, content: MessageContent.System, previous: MessageEntity?) {
        persistMessage(federationSystemMessage(id, conversation.toModel(), self, content, previous?.date ?: DateTimeUtil.currentInstant()))
            .orThrow()
    }

    private companion object {
        const val DOMAIN_COUNT = 2
    }
}

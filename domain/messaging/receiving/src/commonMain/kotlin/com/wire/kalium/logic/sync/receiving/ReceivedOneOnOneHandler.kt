/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.selectOneOnOneProtocol
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.db.MessageEffectDAOs
import com.wire.kalium.util.DateTimeUtil

/** Resolves against canonical conversations and native groups within the owned attempt. */
internal class ReceivedOneOnOneHandler(
    private val self: UserId,
    private val conversations: ReceivedConversationHandler,
    private val users: MembershipUserLoader,
    private val mls: ReceivedMLSHandler,
) {
    @Suppress("ReturnCount")
    suspend fun resolve(id: ConversationId, payloadId: String, context: MlsCoreCryptoContext?): List<ReceivedPayload> {
        if (conversations.conversation(id)?.type != ConversationEntity.Type.ONE_ON_ONE) return emptyList()
        val peer = conversations.members(id).first { it != self.toDao() }.toModel()
        return resolvePeer(peer, payloadId, context)
    }

    suspend fun resolvePeer(peer: UserId, payloadId: String, context: MlsCoreCryptoContext?): List<ReceivedPayload> {
        val knownPeer = users.user(peer)
        if (knownPeer?.deleted == true) {
            return mls.cleanupDeletedPeer(context, checkNotNull(knownPeer.activeOneOnOneConversationId).toModel())
        }
        val profiles = users.refresh(peer)
        return listOf(ReceivedPayload.Profiles(profiles), ReceivedPayload.OneOnOne(selectResolution(peer, payloadId, context)))
    }

    /** The app's batch query excludes deleted peers and does not invalidate each profile again. */
    suspend fun resolveAll(payloadId: String, context: MlsCoreCryptoContext?): List<OneOnOneResolution> =
        users.otherUserIds().filter { peer ->
            users.user(peer)?.deleted == false && conversations.conversationsWith(peer).any {
                it.type == ConversationEntity.Type.ONE_ON_ONE
            }
        }.mapNotNull { peer ->
            try {
                selectResolution(peer, "$payloadId:${peer.value}@${peer.domain}", context)
            } catch (failure: KaliumSyncException) {
                if (failure.coreFailureCause is CoreFailure.NoCommonProtocolFound) null else throw failure
            }
        }

    private suspend fun selectResolution(peer: UserId, payloadId: String, context: MlsCoreCryptoContext?): OneOnOneResolution {
        // A connection 404 stages its SQL placeholder later; absent protocols use the app fallback.
        val other = users.user(peer)
        val selfProtocols = checkNotNull(users.user(self)).supportedProtocols.orEmpty().map {
            SupportedProtocol.valueOf(it.name)
        }.toSet()
        val peerProtocols = other?.supportedProtocols.orEmpty().map { SupportedProtocol.valueOf(it.name) }.toSet()
        val selected = selectOneOnOneProtocol(selfProtocols, peerProtocols)
        val protocol = if (selected is Either.Left && selected.value == CoreFailure.NoCommonProtocolFound.OtherNeedToUpdate) {
            SupportedProtocol.PROTEUS
        } else selected.orThrow()
        val candidates = conversations.conversationsWith(peer).filter { it.type == ConversationEntity.Type.ONE_ON_ONE }
        val proteus = candidates.filter { it.protocolInfo is ConversationEntity.ProtocolInfo.Proteus }.map { it.id }
        val target = when (protocol) {
            SupportedProtocol.PROTEUS -> checkNotNull(proteus.firstOrNull()) {
                "One-to-one resolution requires conversation creation"
            }
            SupportedProtocol.MLS -> checkNotNull(
                candidates.firstOrNull { candidate ->
                    val info = candidate.protocolInfo
                    info is ConversationEntity.ProtocolInfo.MLS && info.groupState == ConversationEntity.GroupState.ESTABLISHED &&
                        checkNotNull(context) { "MLS was not prepared" }.conversationExists(info.groupId)
                }?.id
            ) { "One-to-one resolution requires MLS establishment or external commit response replay" }
        }
        val sources = if (protocol == SupportedProtocol.MLS) proteus else emptyList()
        return OneOnOneResolution(peer, target, sources, protocol, payloadId)
    }
}

internal data class OneOnOneResolution(
    val peer: UserId,
    val target: ConversationIDEntity,
    val sources: List<ConversationIDEntity>,
    val protocol: SupportedProtocol,
    val payloadId: String,
) {
    suspend fun persist(data: MessageEffectDAOs, self: UserId, persistMessage: PersistMessageUseCase): Boolean {
        // This is the app's active-conversation rule, not a receiving completion marker.
        if (checkNotNull(data.users.getUserDetailsByQualifiedID(peer.toDao())).activeOneOnOneConversationId == target) return false
        if (protocol == SupportedProtocol.MLS) {
            sources.forEach { data.messages.moveMessages(from = it, to = target) }
            if (sources.isNotEmpty()) data.conversations.updateConversationModifiedDateToMaxOfSources(target, sources)
        }
        data.users.updateActiveOneOnOneConversation(peer.toDao(), target)
        if (protocol == SupportedProtocol.MLS) {
            listOf(
                self to MessageContent.ConversationStartedUnverifiedWarning,
                peer to MessageContent.ConversationProtocolChanged(Conversation.Protocol.MLS)
            ).forEachIndexed { index, (sender, content) ->
                val message = Message.System(
                    id = "$payloadId:one-to-one:$index",
                    content = content,
                    conversationId = target.toModel(),
                    date = DateTimeUtil.currentInstant(),
                    senderUserId = sender,
                    status = Message.Status.Sent,
                    visibility = Message.Visibility.VISIBLE,
                    expirationData = null,
                )
                persistMessage(message).orThrow()
            }
        }
        return true
    }
}

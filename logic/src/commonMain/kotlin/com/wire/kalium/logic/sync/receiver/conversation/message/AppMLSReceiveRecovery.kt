/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.E2EIConversationState
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.actualMLSVerificationStatus
import com.wire.kalium.logic.data.conversation.areMLSIdentitiesVerified
import com.wire.kalium.logic.data.conversation.mlsVerificationMessage
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.conversation.mlsEpochWarningMessage
import com.wire.kalium.logic.data.conversation.toModel
import com.wire.kalium.logic.data.event.EventEnvelope
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.sync.receiving.MLSReceiveRecovery
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.util.ConversationPersistenceApi

/** Only the durable queue worker can guarantee receipt writes and atomic acknowledgement. */
internal class DurableMLSReceiveContext(
    context: CryptoTransactionContext,
    val effectsAlreadyPersisted: Boolean = false,
) : CryptoTransactionContext by context

/** Main-app effects for the recovery coordinator shared with the notification extension. */
@Suppress("LongParameterList")
internal class AppMLSReceiveRecovery(
    private val recovery: MLSReceiveRecovery,
    private val conversations: ConversationRepository,
    private val mlsConversations: MLSConversationRepository,
    private val persistMessage: PersistMessageUseCase,
    private val selfUserId: UserId,
    private val selfTeamId: SelfTeamIdProvider,
) {
    @Suppress("ReturnCount") // Receipt, resume, and WrongEpoch have intentionally distinct preflight paths.
    @OptIn(ConversationPersistenceApi::class)
    suspend fun handle(context: CryptoTransactionContext, event: Event.Conversation.NewMLSMessage, wrongEpoch: Boolean): Boolean {
        val mls = context.mls ?: return false
        // Receipts precede even canonical metadata reads: a later recovery may have removed this group.
        if (recovery.wasProcessed(mls, event)) return true
        val resumed = recovery.resume(mls, event)
        val result = resumed ?: if (wrongEpoch && event.subconversationId == null) {
            val protocol = conversations.getConversationProtocolInfo(event.conversationId).recoveryOrThrow()
            check(protocol is Conversation.ProtocolInfo.MLSCapable) { "Recovery requires an existing MLS group" }
            recovery.recover(mls, event, protocol.groupId.value) ?: return true
        } else return false

        val teamId = selfTeamId().recoveryOrThrow()
        val entity = MapperProvider.conversationMapper(selfUserId).fromApiModelToDaoModel(
            result.response,
            mlsGroupState = ConversationEntity.GroupState.ESTABLISHED,
            selfUserTeamId = teamId,
        )
        conversations.persistConversations(listOf(entity)).recoveryOrThrow()
        conversations.updateConversationMembers(listOf(result.response), teamId, invalidateMembers = true).recoveryOrThrow()
        mlsConversations.updateGroupIdAndState(
            event.conversationId,
            GroupID(result.operation.groupId),
            result.epoch.toLong(),
            ConversationEntity.GroupState.ESTABLISHED
        ).recoveryOrThrow()
        persistVerification(mls, event, result.operation.groupId, result.warningId)
        persistMessage(mlsEpochWarningMessage(result.warningId, event.conversationId, selfUserId, event.messageInstant)).recoveryOrThrow()
        return true
    }

    private suspend fun persistVerification(
        context: MlsCoreCryptoContext,
        event: Event.Conversation.NewMLSMessage,
        groupId: String,
        warningId: String,
    ) {
        val persisted = conversations.getGroupStatusMembersNamesAndHandles(GroupID(groupId)).recoveryOrThrow()
        val nativeState = context.getGroupState(groupId)
        val state = if (nativeState == E2EIConversationState.VERIFIED) {
            val users = context.members(groupId).map { it.userId }.distinct()
            val identities = context.getUserIdentities(groupId, users)
            val verified = users.all { user ->
                val identity = checkNotNull(identities[user.value]) { "MLS member identity was not returned" }
                check(identity.isNotEmpty()) { "MLS member identity was empty" }
                val profile = persisted.members[user.toModel()]
                areMLSIdentitiesVerified(identity, profile?.name, profile?.handle)
            }
            if (verified) Conversation.VerificationStatus.VERIFIED else Conversation.VerificationStatus.NOT_VERIFIED
        } else nativeState.toModel()
        val updated = actualMLSVerificationStatus(state, persisted.mlsVerificationStatus)
        if (updated == persisted.mlsVerificationStatus) return
        if (updated == Conversation.VerificationStatus.VERIFIED || updated == Conversation.VerificationStatus.DEGRADED) {
            persistMessage(
                mlsVerificationMessage("$warningId:mls-verification", event.conversationId, selfUserId, event.messageInstant, updated)
            ).recoveryOrThrow()
            conversations.setDegradedConversationNotifiedFlag(event.conversationId, updated != Conversation.VerificationStatus.DEGRADED)
                .recoveryOrThrow()
        }
        // Keep the old status until both repeatable effects are persisted, so a retry repairs either failure.
        conversations.updateMlsVerificationStatus(updated, event.conversationId).recoveryOrThrow()
    }

    suspend fun wereEffectsProcessed(context: CryptoTransactionContext, envelope: EventEnvelope): Boolean {
        val mls = context.mls
        val identity = envelope.payloadIdentity
        return mls != null && identity != null && recovery.wereEffectsProcessed(mls, identity)
    }

    suspend fun recordProcessed(context: CryptoTransactionContext, events: List<EventEnvelope>) {
        context.mls?.let {
            recovery.recordProcessed(
                it,
                events.map { envelope -> envelope.event }.filterIsInstance<Event.Conversation.NewMLSMessage>(),
                events.mapNotNull { envelope -> envelope.payloadIdentity },
            )
        }
    }

    suspend fun acknowledge(eventIds: List<String>) = recovery.acknowledge(eventIds)
}

internal fun <T> Either<CoreFailure, T>.recoveryOrThrow(): T = when (this) {
    is Either.Left -> throw KaliumSyncException("MLS receive recovery failed", value)
    is Either.Right -> value
}

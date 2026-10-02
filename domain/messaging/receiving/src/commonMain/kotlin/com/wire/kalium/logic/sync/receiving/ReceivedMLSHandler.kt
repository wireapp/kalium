/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.E2EIConversationState
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.cryptography.WireIdentity
import com.wire.kalium.logic.data.conversation.DecryptedMessageBundle
import com.wire.kalium.logic.data.conversation.processMLSWelcome
import com.wire.kalium.logic.data.conversation.decryptMLSMessage
import com.wire.kalium.logic.data.conversation.mlsEpochWarningMessage
import com.wire.kalium.logic.data.conversation.proposalCommitDeadline
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.ProtoContentMapperImpl
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.sync.receiver.conversation.message.MessageUnpackResult
import com.wire.kalium.logic.sync.receiver.conversation.message.unpack
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.conversation.ProposalTimerEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlin.io.encoding.Base64
import kotlin.time.Instant

/** Per-notification native work. No timers, scopes or async epoch observers are created here. */
internal class ReceivedMLSHandler(
    private val database: UserDatabaseBuilder,
    private val selfUserId: UserId,
    private val conversations: ReceivedConversationHandler,
    private val recovery: MLSReceiveRecovery? = null,
) {
    private val mapper = ProtoContentMapperImpl(selfUserId = selfUserId)
    private val welcomed = mutableSetOf<ConversationId>()
    private val joined = mutableSetOf<ConversationId>()
    private val removed = mutableSetOf<ConversationId>()
    private val inactive = mutableSetOf<ConversationId>()
    private val decryptedConversations = mutableSetOf<ConversationId>()
    private val touchedGroups = mutableSetOf<String>()
    private val wipedGroups = mutableSetOf<String>()
    private val verifications = linkedMapOf<String, MLSVerification>()

    suspend fun resume(context: MlsCoreCryptoContext, event: Event.Conversation.NewMLSMessage): List<ReceivedPayload>? {
        val receiver = recovery ?: return null
        return if (receiver.wasProcessed(context, event)) emptyList() else {
            check(event.conversationId !in removed) { "Account no longer belongs to the MLS group" }
            receiver.resume(context, event)?.let { recovered(context, event, it) }
        }
    }

    private suspend fun recovered(
        context: MlsCoreCryptoContext,
        event: Event.Conversation.NewMLSMessage,
        result: MLSReceiveRecoveryResult,
    ): List<ReceivedPayload> {
        val metadata = conversations.recovered(result.response, context)
        val groupId = result.operation.groupId
        snapshotVerification(context, event.conversationId, groupId, result.warningId, event.messageInstant)
        touchedGroups += groupId
        decryptedConversations += event.conversationId
        return listOf(
            metadata,
            ReceivedPayload.System(mlsEpochWarningMessage(result.warningId, event.conversationId, selfUserId, event.messageInstant))
        )
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    suspend fun decrypt(context: MlsCoreCryptoContext, event: Event.Conversation.NewMLSMessage, payloadId: String): List<ReceivedPayload> {
        check(event.subconversationId == null) { "Subconversation receiving is not implemented" }
        check(event.conversationId !in removed) { "Account no longer belongs to the MLS group" }
        val conversation = checkNotNull(conversations.conversation(event.conversationId)) {
            "Conversation must already exist"
        }
        check(!conversation.hasIncompleteMetadata) { "Conversation requires synchronization" }
        val protocol = checkNotNull(conversation.protocolInfo as? ConversationEntity.ProtocolInfo.MLSCapable)
        val removalId = "$payloadId:mls-removal"
        val previousRemoval = database.messageDAO.getMessageById(removalId, conversation.id)
        // CoreCrypto wipes an inactive group. The canonical lost-epoch warning guards this
        // exact payload on replay; a device removal alone does not remove the account member.
        if (previousRemoval != null && !context.conversationExists(protocol.groupId)) {
            check(
                previousRemoval is MessageEntity.System &&
                previousRemoval.content == MessageEntityContent.MLSWrongEpochWarning &&
                previousRemoval.senderUserId == selfUserId.toDao()
            ) { "MLS removal identity conflicts with stored state" }
            removed += event.conversationId
            return emptyList()
        }
        check(
            protocol.groupState == ConversationEntity.GroupState.ESTABLISHED || event.conversationId in welcomed || previousRemoval != null
        ) {
            "MLS conversation requires recovery"
        }
        // Team removal changes SQL membership without removing the native client.
        // MLS decryption and the lifecycle state above determine whether receiving can continue.
        val epoch = context.conversationEpoch(protocol.groupId)
        val result = decryptMLSMessage(context, Base64.decode(event.content), GroupID(protocol.groupId))
        if (result is Either.Left && result.value == MLSFailure.WrongEpoch) {
            recovery?.recover(context, event, protocol.groupId)?.let { return recovered(context, event, it) }
        }
        val staleCommit = result is Either.Left && result.value == MLSFailure.StaleCommit
        val noBundles = result is Either.Left && result.value in listOf(
            MLSFailure.StaleCommit, MLSFailure.StaleProposal, MLSFailure.DuplicateMessage,
            MLSFailure.BufferedFutureMessage, MLSFailure.BufferedCommit,
            MLSFailure.SelfCommitIgnored, MLSFailure.MessageEpochTooOld,
        )
        // Native buffers commit with this attempt. Later decrypt operations in NSE or the
        // app return their bundles through the same persistence path as current messages.
        val bundles = if (noBundles) emptyList() else result.orThrow()
        val effects = mutableListOf<ReceivedPayload>()
        var bundleEpoch = epoch
        bundles.forEach { bundle ->
            check(event.conversationId !in removed) { "MLS work follows an inactive removal commit" }
            when (bundle) {
                is DecryptedMessageBundle.Proposal -> bundle.commitDelay?.let { delay ->
                    effects += ReceivedPayload.Proposal(
                        ProposalTimerEntity(protocol.groupId, proposalCommitDeadline(event.messageInstant, delay))
                    )
                }
                is DecryptedMessageBundle.Commit -> {
                    if (!bundle.isActive) {
                        check(event.conversationId !in welcomed && event.conversationId !in decryptedConversations && effects.isEmpty()) {
                            "An inactive commit after other MLS payloads requires app synchronization"
                        }
                        effects += inactiveCommit(context, event, protocol.groupId, bundleEpoch, payloadId, removalId)
                    }
                    bundleEpoch++
                }
                is DecryptedMessageBundle.Text -> effects += ReceivedPayload.Application(
                    bundle.unpack(event.conversationId, event.messageInstant, mapper) as MessageUnpackResult.ApplicationMessage,
                    isMLS = true,
                )
            }
        }
        if (event.conversationId !in removed && (staleCommit || context.conversationEpoch(protocol.groupId) != epoch)) {
            snapshotVerification(context, event.conversationId, protocol.groupId, payloadId, event.messageInstant)
        }
        decryptedConversations += event.conversationId
        touchedGroups += protocol.groupId
        return effects
    }

    suspend fun welcome(context: MlsCoreCryptoContext, content: EventContentDTO.Conversation.MLSWelcomeDTO, payloadId: String): Boolean {
        val id = content.qualifiedConversation.toModel()
        check(id !in removed || id in inactive) { "Welcome cannot recover a removed MLS group" }
        val conversation = checkNotNull(conversations.conversation(id)) { "Welcome requires conversation metadata" }
        check(!conversation.hasIncompleteMetadata && (conversation.removedBy == null || id in joined))
        check(
            conversation.type.isGroup || conversation.type in listOf(ConversationEntity.Type.ONE_ON_ONE, ConversationEntity.Type.SELF)
        ) {
            "Welcome requires unsupported conversation lifecycle work"
        }
        val protocol = checkNotNull(conversation.protocolInfo as? ConversationEntity.ProtocolInfo.MLSCapable) {
            "Welcome requires an MLS protocol"
        }
        check(
            id in joined || conversations.includesSelf(id) || database.memberDAO.getMemberRole(selfUserId.toDao(), conversation.id) != null
        ) {
            "Welcome requires current self membership"
        }
        // A new welcome may precede its reset event while the old native group still exists.
        // On replay, the consumed key package instead yields OrphanWelcome; an established
        // canonical group retains its newer epoch, as in the app's duplicate handling.
        val result = processMLSWelcome(context, content.message)
        val groupId = if (result is Either.Left &&
            result.value in listOf(MLSFailure.OrphanWelcome, MLSFailure.ConversationAlreadyExists) &&
            context.conversationExists(protocol.groupId)
        ) protocol.groupId else result.orThrow()
        check(conversations.conversationByGroupId(groupId)?.id in listOf(null, conversation.id)) {
            "Welcome group belongs to another conversation"
        }
        check(groupId !in wipedGroups) { "Recreating a wiped group in one notification requires native replay support" }
        if (groupId == protocol.groupId && inactive.remove(id)) removed.remove(id)
        welcomed += id
        touchedGroups += groupId
        snapshotVerification(context, id, groupId, payloadId, content.time ?: DateTimeUtil.currentInstant())
        conversations.established(id, groupId, context.conversationEpoch(groupId))
        return result is Either.Right
    }

    suspend fun reset(context: MlsCoreCryptoContext, content: EventContentDTO.Conversation.MlsResetConversationDTO): ReceivedPayload {
        val id = content.qualifiedConversation.toModel()
        val oldGroup = content.data.groupId
        val newGroup = content.data.newGroupId
        check(oldGroup !in touchedGroups) { "Reset after earlier old-group MLS payloads requires native replay support" }
        check(conversations.conversationByGroupId(newGroup)?.id in listOf(null, id.toDao())) {
            "Reset group belongs to another conversation"
        }
        // An absent old group is canonical after a completed native reset or inactive commit.
        if (context.conversationExists(oldGroup)) context.wipeConversation(oldGroup)
        wipedGroups += oldGroup
        val established = context.conversationExists(newGroup)
        val reset = ReceivedPayload.MLSReset(
            conversationId = id,
            oldGroupId = oldGroup,
            newGroupId = newGroup,
            epoch = if (established) context.conversationEpoch(newGroup) else 0uL,
            state = if (established) ConversationEntity.GroupState.ESTABLISHED else ConversationEntity.GroupState.PENDING_AFTER_RESET,
        )
        conversations.resetGroup(reset)
        if (established && conversations.conversation(id) != null) {
            snapshotVerification(context, id, newGroup, "$id:reset:$newGroup", DateTimeUtil.currentInstant())
        }
        return reset
    }

    suspend fun cleanupDeletedPeer(context: MlsCoreCryptoContext?, id: ConversationId): List<ReceivedPayload> {
        val conversation = checkNotNull(conversations.conversation(id))
        val protocol = conversation.protocolInfo as? ConversationEntity.ProtocolInfo.MLSCapable ?: return emptyList()
        check(protocol.groupId !in touchedGroups) {
            "Deleted-peer wipe after earlier MLS payloads requires native replay support"
        }
        val native = checkNotNull(context) { "Deleted-peer cleanup requires the MLS transaction" }
        if (native.conversationExists(protocol.groupId)) native.wipeConversation(protocol.groupId)
        wipedGroups += protocol.groupId
        removed += id
        return listOf(ReceivedPayload.ClearProposalTimer(protocol.groupId))
    }

    private suspend fun inactiveCommit(
        context: MlsCoreCryptoContext,
        event: Event.Conversation.NewMLSMessage,
        groupId: String,
        epoch: ULong,
        payloadId: String,
        removalId: String,
    ): List<ReceivedPayload> {
        // CoreCrypto 10.4 wipes top-level removal commits, but its recursive
        // buffer restore leaves the inactive group. Finish the same cleanup here.
        if (context.conversationExists(groupId)) context.wipeConversation(groupId)
        // Once the native group is wiped, earlier ciphertext cannot be replayed.
        removed += event.conversationId
        inactive += event.conversationId
        verifications[groupId] = MLSVerification(
            event.conversationId, groupId, epoch + 1u, "$payloadId:mls-verification", event.messageInstant,
            E2EIConversationState.NOT_ENABLED, emptyMap(), active = false
        )
        return listOf(
            ReceivedPayload.ClearProposalTimer(groupId),
            ReceivedPayload.System(mlsEpochWarningMessage(removalId, event.conversationId, selfUserId, event.messageInstant))
        )
    }

    fun join(event: Event.Conversation.MemberJoin) {
        if (event.members.any { it.id == selfUserId }) {
            check(event.conversationId !in removed || event.conversationId in inactive) {
                "Rejoining a removed MLS group requires app synchronization"
            }
            joined += event.conversationId
        }
    }

    fun hasJoined(conversationId: ConversationId): Boolean = conversationId in joined

    suspend fun leave(context: MlsCoreCryptoContext?, event: Event.Conversation.MemberLeave) {
        if (selfUserId !in event.removedList) return
        val conversation = checkNotNull(conversations.conversation(event.conversationId))
        val protocol = conversation.protocolInfo as? ConversationEntity.ProtocolInfo.MLSCapable
        if (protocol != null) {
            val mls = checkNotNull(context) { "Self removal requires the MLS transaction" }
            check(
                event.conversationId !in welcomed &&
                    (event.conversationId !in decryptedConversations || verifications[protocol.groupId]?.active == false)
            ) {
                "Self removal after other MLS payloads requires app synchronization"
            }
            // A preceding inactive commit may already have wiped this native group.
            if (mls.conversationExists(protocol.groupId)) {
                val epoch = mls.conversationEpoch(protocol.groupId)
                mls.wipeConversation(protocol.groupId)
                verifications[protocol.groupId] = MLSVerification(
                    event.conversationId, protocol.groupId, epoch, "${event.systemMessageId}:mls-verification", event.dateTime,
                    E2EIConversationState.NOT_ENABLED, emptyMap(), active = false
                )
            }
        }
        removed += event.conversationId
        inactive.remove(event.conversationId)
        if (protocol != null && verifications[protocol.groupId]?.active != false) verifications.remove(protocol.groupId)
    }

    private suspend fun snapshotVerification(
        context: MlsCoreCryptoContext,
        conversationId: ConversationId,
        groupId: String,
        payloadId: String,
        date: Instant,
    ) {
        val state = context.getGroupState(groupId)
        val identities = if (state == E2EIConversationState.VERIFIED) {
            val users = context.members(groupId).map { it.userId }.distinct()
            val byValue = context.getUserIdentities(groupId, users)
            users.associate { user ->
                user.toModel() to checkNotNull(byValue[user.value]) { "MLS member identity was not returned" }.also {
                    check(it.isNotEmpty()) { "MLS member identity was empty" }
                }
            }
        } else emptyMap()
        verifications[groupId] = MLSVerification(
            conversationId, groupId, context.conversationEpoch(groupId), "$payloadId:mls-verification", date, state, identities
        )
    }

    /** The app's gathered epoch observer reads final group state; use the same final state here. */
    fun verificationUpdates(): List<MLSVerification> = verifications.values.toList()
}

internal data class MLSVerification(
    val conversationId: ConversationId,
    val groupId: String,
    val epoch: ULong,
    val messageId: String,
    val date: Instant,
    val state: E2EIConversationState,
    val identities: Map<UserId, List<WireIdentity>>,
    val active: Boolean = true,
)

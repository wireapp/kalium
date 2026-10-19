/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.pendingaction.PendingActionEntity
import com.wire.kalium.persistence.dao.pendingaction.PendingActionType
import com.wire.kalium.persistence.db.MessageEffectDAOs
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlin.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Account-owned consumer of NSE clear/remove requests. SQL deletion records the native
 * group identity before deleting its conversation. Native wipes are acknowledged only
 * after their enclosing crypto transaction commits. Files may already be absent on retry.
 */
class PendingConversationCleanup(private val database: UserDatabaseBuilder, private val deleteFile: (String) -> Unit) {
    /** Finish an NSE clear on app replay without deleting effects that followed it. */
    suspend fun recover(carrier: QualifiedIDEntity, messageId: String, target: QualifiedIDEntity): Boolean = database.writeMessageEffects {
        val snapshot = metadata.valueByKey(clearReplayKey(carrier, messageId, target)) ?: return@writeMessageEffects false
        for (id in Json.decodeFromString<List<String>>(snapshot)) {
            assets.deleteMessageFiles(target, id, deleteFile)
            messages.deleteClearedMessage(target, id)
        }
        true
    }

    /**
     * Old NSE versions queued the receive time, not the requested cutoff. These rows
     * cannot safely drive deletion. New requests execute and acknowledge atomically
     * with their exact signal snapshot, so only legacy rows can reach this boundary.
     */
    suspend fun discardLegacyRequests() {
        for (type in listOf(PendingActionType.CLEAR_CONVERSATION_CONTENT, PendingActionType.DELETE_CONVERSATION_LOCALLY)) {
            for (action in database.pendingActionDAO.getByActionType(type)) {
                database.pendingActionDAO.acknowledge(type, action)
            }
        }
    }

    /** Caller serializes native mutations with its existing crypto transaction provider. */
    suspend fun wipePending(context: MlsCoreCryptoContext?): List<PendingActionEntity> {
        val pending = database.pendingActionDAO.getByActionType(PendingActionType.WIPE_MLS_GROUP)
        for (action in pending) {
            val groupId = action.qualifiedId.value
            // A newly joined conversation supersedes an old cleanup request. Never wipe
            // the group behind a current canonical conversation (even if IDs were reused).
            if (database.conversationDAO.getConversationByGroupID(groupId) != null) continue
            val mls = checkNotNull(context) { "Pending MLS cleanup requires the account's MLS engine" }
            if (mls.conversationExists(groupId)) mls.wipeConversation(groupId)
        }
        return pending
    }

    suspend fun committed(actions: List<PendingActionEntity>) {
        for (action in actions) database.pendingActionDAO.acknowledge(PendingActionType.WIPE_MLS_GROUP, action)
        if (actions.isNotEmpty()) kaliumLogger.i(
                "NSE_EVENT stage=mls_cleanup_committed count=${actions.size}"
            )
    }
}

/** Called on the notification writer at the signal's position, before later payloads. */
@Suppress("CyclomaticComplexMethod", "ComplexCondition") // Preserve replay/cutoff guards and conditional ACK ordering.
internal suspend fun MessageEffectDAOs.applyConversationCleanup(
    type: PendingActionType,
    action: PendingActionEntity,
    deleteFile: (String) -> Unit,
    replayKey: String? = null,
    preserveConversation: Boolean = false,
) {
    val id = action.qualifiedId
    val through = Instant.fromEpochMilliseconds(action.createdAt)
    val snapshot = replayKey?.let { metadata.valueByKey(it) }
    if (snapshot != null) {
        for (messageId in Json.decodeFromString<List<String>>(snapshot)) {
            assets.deleteMessageFiles(id, messageId, deleteFile)
            messages.deleteClearedMessage(id, messageId)
        }
        pendingActions.acknowledge(type, action)
        return
    }
    val cleared = mutableListOf<String>()
    while (true) {
        val page = messages.getMessageIdsThrough(id, through, CLEANUP_PAGE_SIZE)
        if (page.isEmpty()) break
        for (messageId in page) {
            assets.deleteMessageFiles(id, messageId, deleteFile)
            messages.deleteClearedMessage(id, messageId)
            if (replayKey != null) cleared += messageId
        }
    }
    // A timestamp alone is insufficient on replay: later system messages can have
    // that same time. Retain content-free IDs as a tombstone for this exact signal.
    replayKey?.let { metadata.insertValue(Json.encodeToString(cleared), it) }
    val conversation = conversations.getConversationById(id)
    // A delayed remove must not erase content or a conversation updated after
    // the user's intent. Clearing through the requested time still completes.
    if (type == PendingActionType.DELETE_CONVERSATION_LOCALLY && !preserveConversation && conversation != null &&
        conversation.lastModifiedDate <= through && messages.getMessageIdsThrough(id, Instant.DISTANT_FUTURE, 1).isEmpty()
    ) {
        val protocol = conversation.protocolInfo
        if (protocol is ConversationEntity.ProtocolInfo.MLSCapable) {
            conversations.clearProposalTimer(protocol.groupId)
            pendingActions.upsert(PendingActionType.WIPE_MLS_GROUP, QualifiedIDEntity(protocol.groupId, "mls"), action.createdAt)
        }
        assets.deleteConversationFiles(id, deleteFile)
        conversations.deleteConversationByQualifiedID(id)
    }
    pendingActions.acknowledge(type, action)
}

internal fun clearReplayKey(carrier: QualifiedIDEntity, messageId: String, target: QualifiedIDEntity): String =
    "nse-clear-v1:" + Json.encodeToString(listOf(carrier.value, carrier.domain, messageId, target.value, target.domain))

private const val CLEANUP_PAGE_SIZE = 100L

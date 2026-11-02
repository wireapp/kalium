/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.logic.data.conversation.unverifiedConversationMessage
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.matchesSystemMessage
import com.wire.kalium.logic.data.message.toEntity
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.persistence.dao.message.LocalId
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.db.MessageEffectDAOs
import kotlin.time.Clock

/** Caller must use writeMessageEffects so acceptance, the warning and its outcome commit together. */
suspend fun MessageEffectDAOs.persistAcceptedConnection(
    connection: ConnectionEntity,
    selfUserId: UserId,
    warningId: String = LocalId.generate(),
    previousWarning: MessageEntity? = null,
) {
    require(connection.status == ConnectionEntity.State.ACCEPTED)
    val id = connection.qualifiedConversationId
    val previousStatus = connections.getConnection(id)?.status
    val outcomeKey = "connection.accepted-warning/${connection.qualifiedToId}/$id/${connection.lastUpdateDate}"
    connections.updateConnectionStatus(connection.qualifiedToId, connection.status)
    members.updateOrInsertOneOnOneMember(MemberEntity(connection.qualifiedToId, MemberEntity.Role.Member), id)

    // A committed outcome also preserves intentional legal-hold suppression and later history clearing.
    // Connection status alone cannot prove that either the warning or its suppression was persisted.
    if (metadata.valueByKey(outcomeKey) != null && previousWarning == null) return
    val active = users.getUserDetailsByQualifiedID(connection.qualifiedToId)?.activeOneOnOneConversationId
    if (previousWarning != null) {
        check(previousWarning.conversationId in listOf(id, active)) { "Accepted warning belongs to another conversation" }
        check(previousWarning.matchesSystemMessage(selfUserId.toDao(), MessageEntityContent.ConversationStartedUnverifiedWarning)) {
            "Accepted warning identity conflicts with stored state"
        }
    }
    val existing = findAcceptedWarning(id, active)
    val suppressed = previousStatus == ConnectionEntity.State.MISSING_LEGALHOLD_CONSENT && previousWarning == null
    if (existing == null && !suppressed) {
        val target = previousWarning?.conversationId ?: active ?: id
        val messageId = previousWarning?.id ?: warningId
        check(messages.getMessageById(messageId, target) == null) { "Accepted warning identity conflicts with stored state" }
        messages.insertOrIgnoreMessages(
            listOf(
                unverifiedConversationMessage(
                    messageId,
                    target.toModel(),
                    previousWarning?.date ?: Clock.System.now(),
                    selfUserId,
                ).toEntity()
            )
        )
    }
    metadata.insertValue(if (suppressed && existing == null) "suppressed" else "persisted", outcomeKey)
}

private suspend fun MessageEffectDAOs.findAcceptedWarning(
    original: ConversationIDEntity,
    active: ConversationIDEntity?,
): MessageEntity? = messages.getConversationStartedUnverifiedWarning(original)
    ?: active?.takeIf { it != original }?.let { messages.getConversationStartedUnverifiedWarning(it) }

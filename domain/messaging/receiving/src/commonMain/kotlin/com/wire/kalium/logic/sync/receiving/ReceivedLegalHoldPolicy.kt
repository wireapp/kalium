/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.client.DeviceTypeEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.db.MessageEffectDAOs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray

/**
 * Match the app's cached-client membership policy without creating legal hold notices in the NSE.
 * A required change aborts the enclosing SQL/native transaction. The durable raw event is then
 * still available to the app, so no second outbox or partially committed membership is needed.
 */
internal suspend fun MessageEffectDAOs.requireUnchangedLegalHold(id: QualifiedIDEntity, self: QualifiedIDEntity) {
    if (metadata.hasPendingLegalHoldTransitions()) throw NotificationAppHandoff(NotificationAppWork.LEGAL_HOLD)
    val conversation = checkNotNull(conversations.getConversationById(id))
    val enabled = clients.getClientsOfConversation(id).values.flatten().any { it.deviceType == DeviceTypeEntity.LegalHold }
    val calculated = if (enabled) ConversationEntity.LegalHoldStatus.ENABLED else ConversationEntity.LegalHoldStatus.DISABLED
    val selfIsMember = members.getMemberRole(self, id) != null
    // The app first emits any transition notice, then stores UNKNOWN as DISABLED after self leaves.
    if (conversation.legalHoldStatus != calculated || !selfIsMember && calculated != ConversationEntity.LegalHoldStatus.DISABLED) {
        throw NotificationAppHandoff(NotificationAppWork.LEGAL_HOLD)
    }
}

internal suspend fun MetadataDAO.hasPendingLegalHoldTransitions(): Boolean = listOf(
    "legal-hold-conversation-transitions-v1",
    "legal-hold-user-transitions-v1"
).any { key -> valueByKey(key)?.let { Json.parseToJsonElement(it).jsonArray.isNotEmpty() } ?: false }

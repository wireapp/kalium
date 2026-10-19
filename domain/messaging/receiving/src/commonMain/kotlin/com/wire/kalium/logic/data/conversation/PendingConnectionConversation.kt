/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity

/** The backend does not expose conversation metadata for an incoming request. */
fun ConnectionEntity.pendingConversation(): ConversationEntity = ConversationEntity(
    id = qualifiedConversationId,
    name = null,
    type = ConversationEntity.Type.CONNECTION_PENDING,
    teamId = null,
    protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
    creatorId = from,
    lastNotificationDate = null,
    lastModifiedDate = lastUpdateDate,
    lastReadDate = lastUpdateDate,
    access = emptyList(),
    accessRole = emptyList(),
    receiptMode = ConversationEntity.ReceiptMode.DISABLED,
    messageTimer = null,
    userMessageTimer = null,
    archived = false,
    archivedInstant = null,
    mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
    proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
    legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED,
    channelAccess = null,
    channelAddPermission = null,
    wireCell = null,
    historySharingRetentionSeconds = 0,
)

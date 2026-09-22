/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import kotlin.time.Instant

suspend fun updateMutedStatusLocally(dao: ConversationDAO, id: ConversationId, status: MutedConversationStatus, time: Instant) =
    wrapStorageRequest { dao.updateConversationMutedStatus(id.toDao(), status.toMutedStatusEntity(), time) }

suspend fun updateArchivedStatusLocally(dao: ConversationDAO, id: ConversationId, archived: Boolean, time: Instant) =
    wrapStorageRequest { dao.updateConversationArchivedStatus(id.toDao(), archived, time) }

fun MutedConversationStatus.toMutedStatusEntity(): ConversationEntity.MutedStatus = when (this) {
    MutedConversationStatus.AllAllowed -> ConversationEntity.MutedStatus.ALL_ALLOWED
    MutedConversationStatus.OnlyMentionsAndRepliesAllowed -> ConversationEntity.MutedStatus.ONLY_MENTIONS_AND_REPLIES_ALLOWED
    MutedConversationStatus.AllMuted -> ConversationEntity.MutedStatus.ALL_MUTED
}

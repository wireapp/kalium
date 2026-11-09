/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.persistence.db.MessageEffectDAOs

/** Caller must use writeMessageEffects so the accepted status and the one-to-one member commit together. */
suspend fun MessageEffectDAOs.persistAcceptedConnection(connection: ConnectionEntity) {
    require(connection.status == ConnectionEntity.State.ACCEPTED)
    connections.updateConnectionStatus(connection.qualifiedToId, connection.status)
    members.updateOrInsertOneOnOneMember(
        MemberEntity(connection.qualifiedToId, MemberEntity.Role.Member),
        connection.qualifiedConversationId
    )
}

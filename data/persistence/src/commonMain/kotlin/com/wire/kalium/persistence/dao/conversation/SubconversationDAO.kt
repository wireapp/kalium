/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.dao.conversation

import com.wire.kalium.persistence.dao.QualifiedIDEntity

/** Account-local routing of native MLS subgroup identities; not proof of current server membership. */
interface SubconversationDAO {
    suspend fun upsert(conversationId: QualifiedIDEntity, subconversationId: String, groupId: String)
    suspend fun getGroupId(conversationId: QualifiedIDEntity, subconversationId: String): String?
    suspend fun containsGroup(groupId: String): Boolean
    suspend fun delete(conversationId: QualifiedIDEntity, subconversationId: String)
}

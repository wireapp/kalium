/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.SubconversationId

interface SubconversationRepository {

    suspend fun insertSubconversation(conversationId: ConversationId, subconversationId: SubconversationId, groupId: GroupID)
    suspend fun getSubconversationInfo(conversationId: ConversationId, subconversationId: SubconversationId): GroupID?

}

class SubconversationRepositoryImpl : SubconversationRepository {

    // TODO make access to this thread safe
    private val subconversations = mutableMapOf<Pair<ConversationId, SubconversationId>, GroupID>()

    override suspend fun insertSubconversation(conversationId: ConversationId, subconversationId: SubconversationId, groupId: GroupID) {
        subconversations[Pair(conversationId, subconversationId)] = groupId
    }

    override suspend fun getSubconversationInfo(conversationId: ConversationId, subconversationId: SubconversationId): GroupID? {
        return subconversations[Pair(conversationId, subconversationId)]
    }

}

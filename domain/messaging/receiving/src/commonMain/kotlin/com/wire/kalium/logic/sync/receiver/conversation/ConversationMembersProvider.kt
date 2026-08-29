/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.persistence.dao.member.MemberDAO
import com.wire.kalium.persistence.dao.member.MemberEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

public fun interface ConversationMembersProvider {
    public suspend fun observeConversationMembers(conversationId: ConversationId): Flow<List<Conversation.Member>>
}

public class DaoConversationMembersProvider public constructor(
    private val memberDAO: MemberDAO,
) : ConversationMembersProvider {
    override suspend fun observeConversationMembers(conversationId: ConversationId): Flow<List<Conversation.Member>> =
        memberDAO.observeConversationMembers(conversationId.toDao()).map { members ->
            members.map { member ->
                Conversation.Member(
                    id = member.user.toModel(),
                    role = member.role.toModel(),
                )
            }
        }

    private fun MemberEntity.Role.toModel(): Conversation.Member.Role = when (this) {
        MemberEntity.Role.Admin -> Conversation.Member.Role.Admin
        MemberEntity.Role.Member -> Conversation.Member.Role.Member
        is MemberEntity.Role.Unknown -> Conversation.Member.Role.Unknown(name)
    }
}

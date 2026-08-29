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
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.member.MemberDAO
import com.wire.kalium.persistence.dao.member.MemberEntity
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DaoConversationMembersProviderTest {

    @Test
    fun givenQualifiedDaoMembersWithEveryRole_whenObserving_thenEveryEmissionIsMappedExactly() = runTest {
        val memberDAO = mock<MemberDAO>(MockMode.autoUnit)
        val firstEmission = listOf(
            MemberEntity(ADMIN_USER_ENTITY_ID, MemberEntity.Role.Admin),
            MemberEntity(MEMBER_USER_ENTITY_ID, MemberEntity.Role.Member),
        )
        val secondEmission = listOf(
            MemberEntity(UNKNOWN_USER_ENTITY_ID, MemberEntity.Role.Unknown(UNKNOWN_ROLE_NAME)),
        )
        everySuspend {
            memberDAO.observeConversationMembers(eq(CONVERSATION_ENTITY_ID))
        } returns flowOf(firstEmission, secondEmission)
        val provider = DaoConversationMembersProvider(memberDAO)

        val result = provider.observeConversationMembers(CONVERSATION_ID).toList()

        assertEquals(
            listOf(
                listOf(
                    Conversation.Member(ADMIN_USER_ID, Conversation.Member.Role.Admin),
                    Conversation.Member(MEMBER_USER_ID, Conversation.Member.Role.Member),
                ),
                listOf(
                    Conversation.Member(UNKNOWN_USER_ID, Conversation.Member.Role.Unknown(UNKNOWN_ROLE_NAME)),
                ),
            ),
            result,
        )
        verifySuspend(VerifyMode.exactly(1)) {
            memberDAO.observeConversationMembers(eq(CONVERSATION_ENTITY_ID))
        }
    }

    private companion object {
        const val UNKNOWN_ROLE_NAME = "custom-role"
        val CONVERSATION_ID = ConversationId("conversation-id", "conversation.example")
        val CONVERSATION_ENTITY_ID = ConversationIDEntity("conversation-id", "conversation.example")
        val ADMIN_USER_ID = UserId("admin-id", "admin.example")
        val ADMIN_USER_ENTITY_ID = UserIDEntity("admin-id", "admin.example")
        val MEMBER_USER_ID = UserId("member-id", "member.example")
        val MEMBER_USER_ENTITY_ID = UserIDEntity("member-id", "member.example")
        val UNKNOWN_USER_ID = UserId("unknown-id", "unknown.example")
        val UNKNOWN_USER_ENTITY_ID = UserIDEntity("unknown-id", "unknown.example")
    }
}

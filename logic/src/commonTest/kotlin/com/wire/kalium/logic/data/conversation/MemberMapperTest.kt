/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.authenticated.conversation.ConversationMembersResponse
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.wire.kalium.network.api.model.UserId as UserIdDTO

class MemberMapperTest {

        val idMapper = mock(IdMapper::class)

        private val roleMapper: ConversationRoleMapper = mock(ConversationRoleMapper::class)

    private lateinit var memberMapper: MemberMapper

    @BeforeTest
    fun setup() {
        every {
            roleMapper.fromApi("wire_admin") 
        }.returns(Conversation.Member.Role.Admin)

        every {
            roleMapper.fromApi("wire_member") 
        }.returns(Conversation.Member.Role.Member)

        memberMapper = MemberMapperImpl(idMapper, roleMapper)
    }

    @Test
    fun givenAMembersResponse_whenMappingFromApiModel_shouldCallIdMapperForAllMembers() {
        val membersResponse = MEMBERS_RESPONSE
        memberMapper.fromApiModel(membersResponse)

        verify {
            roleMapper.fromApi(OTHER_MEMBER_RESPONSE.conversationRole)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAMembersResponse_whenMappingFromApiModel_shouldUseIdReturnedFromMapperAllMembers() {
        val membersResponse = MEMBERS_RESPONSE
        val otherID = QualifiedID("other1", "domain1")
        val selfID = QualifiedID("selfId", "selfDomain")

        val result = memberMapper.fromApiModel(membersResponse)

        assertEquals(otherID, result.otherMembers.first().id)
        assertEquals(selfID, result.self.id)
        assertEquals(OTHER_MEMBER.role, result.otherMembers.first().role)
    }

    @Test
    fun givenAMembersResponseWithNoOthers_whenMappingFromApiModel_shouldReturnNoOthers() {
        val membersResponse = MEMBERS_RESPONSE.copy(otherMembers = listOf())
        val result = memberMapper.fromApiModel(membersResponse)

        assertTrue(result.otherMembers.isEmpty())
    }

    @Test
    @Suppress("MagicNumber")
    fun givenAMembersResponseWithMultipleOthers_whenMappingFromApiModel_shouldReturnMultipleOthers() {
        val others = MEMBERS_RESPONSE.otherMembers.toMutableList()
        repeat(42) {
            others.add(others.first())
        }
        val membersResponse = MEMBERS_RESPONSE.copy(otherMembers = others)

        val result = memberMapper.fromApiModel(membersResponse)

        assertEquals(others.size, result.otherMembers.size)
    }

    private companion object {
        val SELF_MEMBER_RESPONSE = ConversationMemberDTO.Self(UserIdDTO("selfId", "selfDomain"), "wire_admin")
        val SELF_MEMBER = Conversation.Member(UserId("selfId", "selfDomain"), Conversation.Member.Role.Admin)

        val OTHER_MEMBER_RESPONSE =
            ConversationMemberDTO.Other(id = UserIdDTO("other1", "domain1"), conversationRole = "wire_member", service = null)
        val OTHER_MEMBER = Conversation.Member(id = UserId("other1", "domain1"), role = Conversation.Member.Role.Member)

        val MEMBERS_RESPONSE = ConversationMembersResponse(SELF_MEMBER_RESPONSE, listOf(OTHER_MEMBER_RESPONSE))
        val MEMBERS_INFO = MembersInfo(SELF_MEMBER, listOf(OTHER_MEMBER))
    }
}

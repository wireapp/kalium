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
package com.wire.kalium.logic.util.arrangement.dao

import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.member.MemberDAO
import com.wire.kalium.persistence.dao.member.MemberEntity
import io.mockative.Mock
import io.mockative.any
import io.mockative.fun2
import io.mockative.given
import io.mockative.matchers.Matcher
import io.mockative.mock
import kotlinx.coroutines.flow.Flow

interface MemberDAOArrangement {
    @Mock
    val memberDAO: MemberDAO

    fun withUpdateOrInsertOneOnOneMemberWithConnectionStatusSuccess(
        member: Matcher<MemberEntity> = any(),
        status: Matcher<ConnectionEntity.State> = any(),
        conversationId: Matcher<QualifiedIDEntity> = any()
    )

    fun withUpdateOrInsertOneOnOneMemberWithConnectionStatusFailure(
        error: Throwable,
        member: Matcher<MemberEntity> = any(),
        status: Matcher<ConnectionEntity.State> = any(),
        conversationId: Matcher<QualifiedIDEntity> = any()
    )

    fun withUpdateMemberRoleSuccess(
        conversationId: Matcher<QualifiedIDEntity> = any(),
        userId: Matcher<UserIDEntity> = any(),
        role: Matcher<MemberEntity.Role> = any()
    )

    fun withObserveIsUserMember(
        expectedIsUserMember: Flow<Boolean>,
        userId: Matcher<UserIDEntity> = any(),
        conversationId: Matcher<QualifiedIDEntity> = any()
    )

    fun withInsertMemberWithConversationIdSuccess(
        conversationId: Matcher<QualifiedIDEntity> = any(),
        membersList: Matcher<List<MemberEntity>> = any()
    )

    fun withObserveConversationMembers(
        result: Flow<List<MemberEntity>>,
        conversationId: Matcher<QualifiedIDEntity> = any()
    )

    fun withDeleteMembersByQualifiedID(
        conversationId: Matcher<QualifiedIDEntity> = any(),
        memberIdList: Matcher<List<QualifiedIDEntity>> = any()
    )
}

class MemberDAOArrangementImpl : MemberDAOArrangement {
    @Mock
    override val memberDAO: MemberDAO = mock(MemberDAO::class)

    override fun withUpdateOrInsertOneOnOneMemberWithConnectionStatusSuccess(
        member: Matcher<MemberEntity>,
        status: Matcher<ConnectionEntity.State>,
        conversationId: Matcher<QualifiedIDEntity>
    ) {
        given(memberDAO)
            .suspendFunction(memberDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
            .whenInvokedWith(member, status, conversationId)
    }

    override fun withUpdateOrInsertOneOnOneMemberWithConnectionStatusFailure(
        error: Throwable,
        member: Matcher<MemberEntity>,
        status: Matcher<ConnectionEntity.State>,
        conversationId: Matcher<QualifiedIDEntity>
    ) {
        given(memberDAO)
            .suspendFunction(memberDAO::updateOrInsertOneOnOneMemberWithConnectionStatus)
            .whenInvokedWith(member, status, conversationId)
            .thenThrow(error)
    }


    override fun withUpdateMemberRoleSuccess(
        conversationId: Matcher<QualifiedIDEntity>,
        userId: Matcher<UserIDEntity>,
        role: Matcher<MemberEntity.Role>
    ) {
        given(memberDAO)
            .suspendFunction(memberDAO::updateConversationMemberRole)
            .whenInvokedWith(conversationId, userId, role)
            .thenReturn(Unit)
    }

    override fun withObserveIsUserMember(
        result: Flow<Boolean>,
        userId: Matcher<UserIDEntity>,
        conversationId: Matcher<QualifiedIDEntity>
    ) {
        given(memberDAO)
            .suspendFunction(memberDAO::observeIsUserMember)
            .whenInvokedWith(userId, conversationId)
            .thenReturn(result)
    }

    override fun withInsertMemberWithConversationIdSuccess(
        conversationId: Matcher<QualifiedIDEntity>,
        membersList: Matcher<List<MemberEntity>>
    ) {
        given(memberDAO)
            .suspendFunction(memberDAO::insertMembersWithQualifiedId, fun2<List<MemberEntity>, QualifiedIDEntity>())
            .whenInvokedWith(membersList, conversationId)
            .thenReturn(Unit)
    }

    override fun withObserveConversationMembers(
        result: Flow<List<MemberEntity>>,
        conversationId: Matcher<QualifiedIDEntity>
    ) {
        given(memberDAO)
            .suspendFunction(memberDAO::observeConversationMembers)
            .whenInvokedWith(conversationId)
            .thenReturn(result)
    }

    override fun withDeleteMembersByQualifiedID(
        conversationId: Matcher<QualifiedIDEntity>,
        memberIdList: Matcher<List<QualifiedIDEntity>>
    ) {
        given(memberDAO)
            .suspendFunction(memberDAO::deleteMembersByQualifiedID)
            .whenInvokedWith(memberIdList, conversationId)
            .thenReturn(Unit)
    }
}



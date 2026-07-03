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
package com.wire.kalium.logic.util.arrangement.dao

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.member.MemberDAO
import com.wire.kalium.persistence.dao.member.MemberEntity
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import kotlinx.coroutines.flow.Flow

interface MemberDAOArrangement {
    val memberDAO: MemberDAO

    suspend fun withUpdateOrInsertOneOnOneMemberSuccess(
        member: (MemberEntity) -> Boolean = { true },
        conversationId: (QualifiedIDEntity) -> Boolean = { true }
    )

    suspend fun withUpdateOrInsertOneOnOneMemberFailure(
        error: Throwable,
        member: (MemberEntity) -> Boolean = { true },
        conversationId: (QualifiedIDEntity) -> Boolean = { true }
    )

    suspend fun withUpdateMemberRoleSuccess(
        conversationId: (QualifiedIDEntity) -> Boolean = { true },
        userId: (UserIDEntity) -> Boolean = { true },
        role: (MemberEntity.Role) -> Boolean = { true }
    )

    suspend fun withObserveIsUserMember(
        expectedIsUserMember: Flow<Boolean>,
        userId: (UserIDEntity) -> Boolean = { true },
        conversationId: (QualifiedIDEntity) -> Boolean = { true }
    )

    suspend fun withInsertMemberWithConversationIdSuccess(
        conversationId: (QualifiedIDEntity) -> Boolean = { true },
        membersList: (List<MemberEntity>) -> Boolean = { true }
    )

    suspend fun withObserveConversationMembers(
        result: Flow<List<MemberEntity>>,
        conversationId: (QualifiedIDEntity) -> Boolean = { true }
    )

    suspend fun withGetMemberRole(
        result: MemberEntity.Role?,
        userId: (UserIDEntity) -> Boolean = { true },
        conversationId: (QualifiedIDEntity) -> Boolean = { true }
    )

    suspend fun withDeleteMembersByQualifiedID(
        result: Long,
        conversationId: (QualifiedIDEntity) -> Boolean = { true },
        memberIdList: (List<QualifiedIDEntity>) -> Boolean = { true }
    )

    suspend fun withDeleteMembersByQualifiedIDThrows(
        throws: Throwable,
        conversationId: (QualifiedIDEntity) -> Boolean = { true },
        memberIdList: (List<QualifiedIDEntity>) -> Boolean = { true }
    )
}

class MemberDAOArrangementImpl : MemberDAOArrangement {
    override val memberDAO: MemberDAO = mock<MemberDAO>(mode = MockMode.autoUnit)

    override suspend fun withUpdateOrInsertOneOnOneMemberSuccess(
        member: (MemberEntity) -> Boolean,
        conversationId: (QualifiedIDEntity) -> Boolean
    ) {
        everySuspend {
            memberDAO.updateOrInsertOneOnOneMember(
                matches { member(it) },
                matches { conversationId(it) }
            )
        } returns Unit
    }

    override suspend fun withUpdateOrInsertOneOnOneMemberFailure(
        error: Throwable,
        member: (MemberEntity) -> Boolean,
        conversationId: (QualifiedIDEntity) -> Boolean
    ) {
        everySuspend {
            memberDAO.updateOrInsertOneOnOneMember(
                matches { member(it) },
                matches { conversationId(it) }
            )
        } throws error
    }

    override suspend fun withUpdateMemberRoleSuccess(
        conversationId: (QualifiedIDEntity) -> Boolean,
        userId: (UserIDEntity) -> Boolean,
        role: (MemberEntity.Role) -> Boolean
    ) {
        everySuspend {
            memberDAO.updateConversationMemberRole(
                matches {
                    conversationId(it)
                },
                matches {
                    userId(it)
                },
                matches {
                    role(it)
                }
            )
        } returns Unit
    }

    override suspend fun withObserveIsUserMember(
        expectedIsUserMember: Flow<Boolean>,
        userId: (UserIDEntity) -> Boolean,
        conversationId: (QualifiedIDEntity) -> Boolean
    ) {
        everySuspend {
            memberDAO.observeIsUserMember(
                matches { conversationId(it) },
                matches { userId(it) }
            )
        } returns expectedIsUserMember
    }

    override suspend fun withInsertMemberWithConversationIdSuccess(
        conversationId: (QualifiedIDEntity) -> Boolean,
        membersList: (List<MemberEntity>) -> Boolean
    ) {
        everySuspend {
            memberDAO.insertMembersWithQualifiedId(
                matches { membersList(it) },
                matches { conversationId(it) }
            )
        } returns Unit
    }

    override suspend fun withObserveConversationMembers(
        result: Flow<List<MemberEntity>>,
        conversationId: (QualifiedIDEntity) -> Boolean
    ) {
        everySuspend {
            memberDAO.observeConversationMembers(matches { conversationId(it) })
        } returns result
    }

    override suspend fun withGetMemberRole(
        result: MemberEntity.Role?,
        userId: (UserIDEntity) -> Boolean,
        conversationId: (QualifiedIDEntity) -> Boolean
    ) {
        everySuspend {
            memberDAO.getMemberRole(
                matches { userId(it) },
                matches { conversationId(it) }
            )
        } returns result
    }

    override suspend fun withDeleteMembersByQualifiedID(
        result: Long,
        conversationId: (QualifiedIDEntity) -> Boolean,
        memberIdList: (List<QualifiedIDEntity>) -> Boolean
    ) {
        everySuspend {
            memberDAO.deleteMembersByQualifiedID(
                matches { memberIdList(it) },
                matches { conversationId(it) }
            )
        } returns result
    }

    override suspend fun withDeleteMembersByQualifiedIDThrows(
        throws: Throwable,
        conversationId: (QualifiedIDEntity) -> Boolean,
        memberIdList: (List<QualifiedIDEntity>) -> Boolean
    ) {
        everySuspend {
            memberDAO.deleteMembersByQualifiedID(
                matches { memberIdList(it) },
                matches { conversationId(it) }
            )
        } throws throws
    }
}

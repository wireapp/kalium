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
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.fake.valueOf
import io.mockative.matchers.AnyMatcher
import io.mockative.matchers.Matcher
import io.mockative.matches
import io.mockative.mock
import kotlinx.coroutines.flow.Flow

interface MemberDAOArrangement {
    @Mock
    val memberDAO: MemberDAO

    suspend fun withUpdateOrInsertOneOnOneMemberSuccess(
        member: Matcher<MemberEntity> = AnyMatcher(valueOf()),
        conversationId: Matcher<QualifiedIDEntity> = AnyMatcher(valueOf())
    )

    suspend fun withUpdateOrInsertOneOnOneMemberFailure(
        error: Throwable,
        member: Matcher<MemberEntity> = AnyMatcher(valueOf()),
        conversationId: Matcher<QualifiedIDEntity> = AnyMatcher(valueOf())
    )

    suspend fun withUpdateMemberRoleSuccess(
        conversationId: Matcher<QualifiedIDEntity> = AnyMatcher(valueOf()),
        userId: Matcher<UserIDEntity> = AnyMatcher(valueOf()),
        role: Matcher<MemberEntity.Role> = AnyMatcher(valueOf())
    )

    suspend fun withObserveIsUserMember(
        expectedIsUserMember: Flow<Boolean>,
        userId: Matcher<UserIDEntity> = AnyMatcher(valueOf()),
        conversationId: Matcher<QualifiedIDEntity> = AnyMatcher(valueOf())
    )

    suspend fun withInsertMemberWithConversationIdSuccess(
        conversationId: Matcher<QualifiedIDEntity> = AnyMatcher(valueOf()),
        membersList: Matcher<List<MemberEntity>> = AnyMatcher(valueOf())
    )

    suspend fun withObserveConversationMembers(
        result: Flow<List<MemberEntity>>,
        conversationId: Matcher<QualifiedIDEntity> = AnyMatcher(valueOf())
    )

    suspend fun withDeleteMembersByQualifiedID(
        result: Long,
        conversationId: Matcher<QualifiedIDEntity> = AnyMatcher(valueOf()),
        memberIdList: Matcher<List<QualifiedIDEntity>> = AnyMatcher(valueOf())
    )

    suspend fun withDeleteMembersByQualifiedIDThrows(
        throws: Throwable,
        conversationId: Matcher<QualifiedIDEntity> = AnyMatcher(valueOf()),
        memberIdList: Matcher<List<QualifiedIDEntity>> = AnyMatcher(valueOf())
    )
}

class MemberDAOArrangementImpl : MemberDAOArrangement {
    @Mock
    override val memberDAO: MemberDAO = mock(MemberDAO::class)

    override suspend fun withUpdateOrInsertOneOnOneMemberSuccess(
        member: Matcher<MemberEntity>,
        conversationId: Matcher<QualifiedIDEntity>
    ) {
        coEvery {
            memberDAO.updateOrInsertOneOnOneMember(
                matches { member.matches(it) },
                matches { conversationId.matches(it) }
            )
        }
    }

    override suspend fun withUpdateOrInsertOneOnOneMemberFailure(
        error: Throwable,
        member: Matcher<MemberEntity>,
        conversationId: Matcher<QualifiedIDEntity>
    ) {
        coEvery {
            memberDAO.updateOrInsertOneOnOneMember(
                matches { member.matches(it) },
                matches { conversationId.matches(it) }
            )
        }.throws(error)
    }

    override suspend fun withUpdateMemberRoleSuccess(
        conversationId: Matcher<QualifiedIDEntity>,
        userId: Matcher<UserIDEntity>,
        role: Matcher<MemberEntity.Role>
    ) {
        coEvery {
            memberDAO.updateConversationMemberRole(
                matches {
                    conversationId.matches(it)
                },
                matches {
                    userId.matches(it)
                },
                matches {
                    role.matches(it)
                }
            )
        }.returns(Unit)
    }

    override suspend fun withObserveIsUserMember(
        expectedIsUserMember: Flow<Boolean>,
        userId: Matcher<UserIDEntity>,
        conversationId: Matcher<QualifiedIDEntity>
    ) {
        coEvery {
            memberDAO.observeIsUserMember(
                matches { userId.matches(it) },
                matches { conversationId.matches(it) }
            )
        }.returns(expectedIsUserMember)
    }

    override suspend fun withInsertMemberWithConversationIdSuccess(
        conversationId: Matcher<QualifiedIDEntity>,
        membersList: Matcher<List<MemberEntity>>
    ) {
        coEvery {
            memberDAO.insertMembersWithQualifiedId(
                matches { membersList.matches(it) },
                matches { conversationId.matches(it) }
            )
        }.returns(Unit)
    }

    override suspend fun withObserveConversationMembers(
        result: Flow<List<MemberEntity>>,
        conversationId: Matcher<QualifiedIDEntity>
    ) {
        coEvery {
            memberDAO.observeConversationMembers(matches { conversationId.matches(it) })
        }.returns(result)
    }

    override suspend fun withDeleteMembersByQualifiedID(
        result: Long,
        conversationId: Matcher<QualifiedIDEntity>,
        memberIdList: Matcher<List<QualifiedIDEntity>>
    ) {
        coEvery {
            memberDAO.deleteMembersByQualifiedID(
                matches { memberIdList.matches(it) },
                matches { conversationId.matches(it) }
            )
        }.returns(result)
    }

    override suspend fun withDeleteMembersByQualifiedIDThrows(
        throws: Throwable,
        conversationId: Matcher<QualifiedIDEntity>,
        memberIdList: Matcher<List<QualifiedIDEntity>>
    ) {
        coEvery {
            memberDAO.deleteMembersByQualifiedID(
                matches { memberIdList.matches(it) },
                matches { conversationId.matches(it) }
            )
        }.throws(throws)
    }
}

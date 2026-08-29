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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.member.MemberDAO
import com.wire.kalium.persistence.dao.member.MemberEntity
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationLifecycleEventRepositoryImplTest {

    @Test
    fun givenConversationWrites_whenApplied_thenValuesAreForwardedToDao() = runTest {
        val (arrangement, repository) = arrangement()
        everySuspend { arrangement.conversationDAO.deleteConversationByQualifiedID(eq(conversationIdEntity)) } returns true

        assertEquals(Either.Right(Unit), repository.updateConversationName(conversationId, "new name", instant))
        assertEquals(Either.Right(Unit), repository.updateConversationModifiedDate(conversationId, instant))
        assertEquals(Either.Right(true), repository.deleteConversationLocally(conversationId))
        assertEquals(Either.Right(Unit), repository.setConversationDeletedLocally(conversationId, false))

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationDAO.updateConversationName(eq(conversationIdEntity), eq("new name"), eq(instant))
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationDAO.updateConversationModifiedDate(eq(conversationIdEntity), eq(instant))
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationDAO.setConversationDeletedLocally(eq(conversationIdEntity), eq(false))
        }
    }

    @Test
    fun givenMemberWrites_whenApplied_thenIdsAndRolesAreMappedToDao() = runTest {
        val (arrangement, repository) = arrangement()
        val members = listOf(
            Conversation.Member(adminUserId, Conversation.Member.Role.Admin),
            Conversation.Member(otherUserId, Conversation.Member.Role.Unknown("custom-role")),
        )
        everySuspend {
            arrangement.memberDAO.deleteMembersByQualifiedID(
                eq(listOf(adminUserIdEntity, otherUserIdEntity)),
                eq(conversationIdEntity),
            )
        } returns 2L

        assertEquals(Either.Right(Unit), repository.persistMembers(members, conversationId))
        assertEquals(Either.Right(2L), repository.deleteMembers(listOf(adminUserId, otherUserId), conversationId))

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.memberDAO.insertMembersWithQualifiedId(
                eq(
                    listOf(
                        MemberEntity(adminUserIdEntity, MemberEntity.Role.Admin),
                        MemberEntity(otherUserIdEntity, MemberEntity.Role.Unknown("custom-role")),
                    )
                ),
                eq(conversationIdEntity),
            )
        }
    }

    @Test
    fun givenMemberRoleReadAndUpdate_whenApplied_thenRolesAreMappedAcrossBoundary() = runTest {
        val (arrangement, repository) = arrangement()
        everySuspend {
            arrangement.memberDAO.getMemberRole(eq(adminUserIdEntity), eq(conversationIdEntity))
        } returns MemberEntity.Role.Unknown("custom-role")

        assertEquals(
            Either.Right(Conversation.Member.Role.Unknown("custom-role")),
            repository.getConversationMemberRole(conversationId, adminUserId),
        )
        assertEquals(
            Either.Right(Unit),
            repository.updateMemberFromEvent(
                Conversation.Member(adminUserId, Conversation.Member.Role.Member),
                conversationId,
            ),
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.memberDAO.updateMemberRole(
                eq(adminUserIdEntity),
                eq(conversationIdEntity),
                eq(MemberEntity.Role.Member),
            )
        }
    }

    @Test
    fun givenMemberStateWrites_whenApplied_thenStatusAndArchiveValuesAreForwarded() = runTest {
        val (arrangement, repository) = arrangement()

        assertEquals(
            Either.Right(Unit),
            repository.updateMutedStatusLocally(conversationId, MutedConversationStatus.OnlyMentionsAndRepliesAllowed, instant),
        )
        assertEquals(Either.Right(Unit), repository.updateArchivedStatusLocally(conversationId, true, instant))

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationDAO.updateConversationMutedStatus(
                eq(conversationIdEntity),
                eq(ConversationEntity.MutedStatus.ONLY_MENTIONS_AND_REPLIES_ALLOWED),
                eq(instant),
            )
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationDAO.updateConversationArchivedStatus(
                eq(conversationIdEntity),
                eq(true),
                eq(instant),
            )
        }
    }

    @Test
    fun givenDaoFailure_whenWritingName_thenStorageFailureIsReturned() = runTest {
        val expectedException = IllegalStateException("storage failure")
        val (arrangement, repository) = arrangement()
        everySuspend {
            arrangement.conversationDAO.updateConversationName(eq(conversationIdEntity), eq("new name"), eq(instant))
        } throws expectedException

        assertEquals(
            Either.Left(StorageFailure.Generic(expectedException)),
            repository.updateConversationName(conversationId, "new name", instant),
        )
    }

    private fun arrangement(): Pair<Arrangement, ConversationLifecycleEventRepository> {
        val arrangement = Arrangement()
        return arrangement to ConversationLifecycleEventRepositoryImpl(
            arrangement.conversationDAO,
            arrangement.memberDAO,
        )
    }

    private class Arrangement {
        val conversationDAO = mock<ConversationDAO>(MockMode.autoUnit)
        val memberDAO = mock<MemberDAO>(MockMode.autoUnit)
    }

    private companion object {
        val conversationId = ConversationId("conversation-id", "wire.example")
        val conversationIdEntity = ConversationIDEntity("conversation-id", "wire.example")
        val adminUserId = UserId("admin-id", "wire.example")
        val adminUserIdEntity = UserIDEntity("admin-id", "wire.example")
        val otherUserId = UserId("other-id", "wire.example")
        val otherUserIdEntity = UserIDEntity("other-id", "wire.example")
        val instant = Instant.parse("2026-08-19T12:00:00Z")
    }
}

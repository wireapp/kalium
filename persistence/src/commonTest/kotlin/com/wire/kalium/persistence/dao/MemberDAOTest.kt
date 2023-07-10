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
package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.member.MemberDAO
import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.utils.stubs.TestStubs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemberDAOTest : BaseDatabaseTest() {

    private lateinit var conversationDAO: ConversationDAO
    private lateinit var messageDAO: MessageDAO
    private lateinit var userDAO: UserDAO
    private lateinit var teamDAO: TeamDAO
    private lateinit var memberDAO: MemberDAO
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, enableWAL = true)
        conversationDAO = db.conversationDAO
        messageDAO = db.messageDAO
        userDAO = db.userDAO
        teamDAO = db.teamDAO
        memberDAO = db.memberDAO
    }

    @Test
    fun givenExistingConversation_ThenMemberCanBeInserted() = runTest {
        val conversationEntity1 = TestStubs.conversationEntity1
        val member1 = TestStubs.member1
        conversationDAO.insertConversation(conversationEntity1)
        memberDAO.insertMember(member1, conversationEntity1.id)

        assertEquals(listOf(member1), memberDAO.observeConversationMembers(conversationEntity1.id).first())
    }


    @Test
    fun givenExistingConversation_ThenMemberCanBeDeleted() = runTest {
        val conversationEntity1 = TestStubs.conversationEntity1
        val member1 = TestStubs.member1

        conversationDAO.insertConversation(conversationEntity1)


        memberDAO.insertMember(member1, conversationEntity1.id)
        memberDAO.deleteMemberByQualifiedID(conversationEntity1.id, member1.user)

        assertEquals(emptyList(), memberDAO.observeConversationMembers(conversationEntity1.id).first())
    }

    @Test
    fun givenExistingConversation_ThenMemberCanBeUpdated() = runTest {
        val conversationEntity1 = TestStubs.conversationEntity1
        val member1 = TestStubs.member1
        val newRole = MemberEntity.Role.Member

        val expected = listOf(member1.copy(role = newRole))
        conversationDAO.insertConversation(conversationEntity1)
        memberDAO.insertMember(member1, conversationEntity1.id)
        memberDAO.updateMemberRole(member1.user, conversationEntity1.id, newRole)

        assertEquals(expected, memberDAO.observeConversationMembers(conversationEntity1.id).first())
    }

    @Test
    fun givenExistingConversation_ThenAllMembersCanBeRetrieved() = runTest {
        val conversationEntity1 = TestStubs.conversationEntity1
        val member1 = TestStubs.member1
        val member2 = TestStubs.member2

        conversationDAO.insertConversation(conversationEntity1)

        memberDAO.insertMembersWithQualifiedId(listOf(member1, member2), conversationEntity1.id)

        assertEquals(setOf(member1, member2), memberDAO.observeConversationMembers(conversationEntity1.id).first().toSet())
    }

    @Test
    fun givenExistingMLSConversation_whenAddingMembersByGroupId_ThenAllMembersCanBeRetrieved() = runTest {
        val conversationEntity2 = TestStubs.conversationEntity2
        val member1 = TestStubs.member1
        val member2 = TestStubs.member2

        conversationDAO.insertConversation(conversationEntity2)

        memberDAO.insertMembers(
            listOf(member1, member2),
            (conversationEntity2.protocolInfo as ConversationEntity.ProtocolInfo.MLS).groupId
        )

        assertEquals(listOf(member1, member2), memberDAO.observeConversationMembers(conversationEntity2.id).first())
    }

    @Test
    fun givenExistingConversation_ThenInsertedOrUpdatedMembersAreRetrieved() = runTest {
        val conversationEntity1 = TestStubs.conversationEntity1
        val member1 = TestStubs.member1

        conversationDAO.insertConversation(conversationEntity1)
        memberDAO.updateOrInsertOneOnOneMemberWithConnectionStatus(
            member = member1,
            status = ConnectionEntity.State.ACCEPTED,
            conversationID = conversationEntity1.id
        )

        assertEquals(
            listOf(member1), memberDAO.observeConversationMembers(conversationEntity1.id).first()
        )
    }

    @Test
    fun givenExistingConversation_ThenUserTableShouldBeUpdatedOnlyAndNotReplaced() = runTest(dispatcher) {
        val conversationEntity1 = TestStubs.conversationEntity1
        val user1 = TestStubs.user1
        val member1 = TestStubs.member1

        conversationDAO.insertConversation(conversationEntity1)
        userDAO.insertUser(user1.copy(connectionStatus = ConnectionEntity.State.NOT_CONNECTED))

        memberDAO.updateOrInsertOneOnOneMemberWithConnectionStatus(
            member = member1,
            status = ConnectionEntity.State.SENT,
            conversationID = conversationEntity1.id
        )

        assertEquals(listOf(member1), memberDAO.observeConversationMembers(conversationEntity1.id).first())
        assertEquals(ConnectionEntity.State.SENT, userDAO.getUserByQualifiedID(user1.id).first()?.connectionStatus)
        assertEquals(user1.name, userDAO.getUserByQualifiedID(user1.id).first()?.name)
    }


    @Test
    fun givenConversation_whenInsertingMembers_thenMembersShouldNotBeDuplicated() = runTest {
        val member1 = TestStubs.member1
        val member2 = TestStubs.member2
        val conversationEntity1 = TestStubs.conversationEntity1

        val expected = listOf(member1, member2)

        conversationDAO.insertConversation(conversationEntity1)

        memberDAO.insertMember(member1, conversationEntity1.id)
        memberDAO.insertMember(member2, conversationEntity1.id)
        memberDAO.observeConversationMembers(conversationEntity1.id).first().also { actual ->
            assertEquals(expected, actual)
        }
        memberDAO.insertMember(member1, conversationEntity1.id)
        memberDAO.insertMember(member2, conversationEntity1.id)
        memberDAO.observeConversationMembers(conversationEntity1.id).first().also { actual ->
            assertEquals(expected, actual)
        }
    }

    @Test
    fun givenMultipleConversation_whenInsertingMembers_thenMembersAreInserted() = runTest {
        val member1 = TestStubs.member1
        val member2 = TestStubs.member2
        val conversationEntity1 = TestStubs.conversationEntity1
        val conversationEntity2 = TestStubs.conversationEntity2

        val expected = listOf(member1, member2)

        conversationDAO.insertConversation(conversationEntity1)
        conversationDAO.insertConversation(conversationEntity2)

        memberDAO.insertMember(member1, conversationEntity1.id)
        memberDAO.insertMember(member2, conversationEntity1.id)
        memberDAO.observeConversationMembers(conversationEntity1.id).first().also { actual ->
            assertEquals(expected, actual)
        }
        memberDAO.insertMember(member1, conversationEntity2.id)
        memberDAO.insertMember(member2, conversationEntity2.id)
        memberDAO.observeConversationMembers(conversationEntity2.id).first().also { actual ->
            assertEquals(expected, actual)
        }
        memberDAO.observeConversationMembers(conversationEntity1.id).first().also { actual ->
            assertEquals(expected, actual)
        }
    }


    @Test
    fun givenMember_whenUpdatingMemberRole_thenItsUpdated() = runTest {
        // given
        val conversation = TestStubs.conversationEntity1

        val member = TestStubs.member1.copy(role = MemberEntity.Role.Member)

        val newRole = MemberEntity.Role.Admin
        val expected = member.copy(role = newRole)
        conversationDAO.insertConversation(conversation)
        memberDAO.insertMember(member, conversation.id)
        // when
        memberDAO.updateConversationMemberRole(conversation.id, member.user, newRole)
        // then
        memberDAO.observeConversationMembers(conversation.id).first().also { actual ->
            assertEquals(expected, actual[0])
        }
    }

    @Test
    fun givenAGroupWithSeveralMembers_whenInvokingIsUserMember_itReturnsACorrectValue() = runTest {
        val conversationEntity1 = TestStubs.conversationEntity1
        val user1 = TestStubs.user1
        val user2 = TestStubs.user2
        val user3 = TestStubs.user3

        val member1 = TestStubs.member1
        val member2 = TestStubs.member2
        val member3 = TestStubs.member3
        // given
        conversationDAO.insertConversation(conversationEntity1)
        userDAO.insertUser(user1)
        userDAO.insertUser(user2)
        userDAO.insertUser(user3)
        memberDAO.insertMember(member1, conversationEntity1.id)
        memberDAO.insertMember(member2, conversationEntity1.id)
        memberDAO.insertMember(member3, conversationEntity1.id)
        memberDAO.deleteMemberByQualifiedID(member2.user, conversationEntity1.id)

        // When
        val isMember = memberDAO.observeIsUserMember(conversationEntity1.id, user3.id).first()

        // then
        assertEquals(true, isMember)
    }


    @Test
    fun givenAGroupWithSeveralMembers_whenRemovingOneAndInvokingIsUserMember_itReturnsAFalseValue() = runTest {
        val conversationEntity1 = TestStubs.conversationEntity1
        val user1 = TestStubs.user1
        val user2 = TestStubs.user2
        val user3 = TestStubs.user3

        val member1 = TestStubs.member1
        val member2 = TestStubs.member2
        val member3 = TestStubs.member3

        // given
        conversationDAO.insertConversation(conversationEntity1)
        userDAO.insertUser(user1)
        userDAO.insertUser(user2)
        userDAO.insertUser(user3)
        memberDAO.insertMember(member1, conversationEntity1.id)
        memberDAO.insertMember(member2, conversationEntity1.id)
        memberDAO.insertMember(member3, conversationEntity1.id)
        memberDAO.deleteMemberByQualifiedID(member3.user, conversationEntity1.id)

        // when
        val isMember = memberDAO.observeIsUserMember(conversationEntity1.id, user3.id).first()

        // then
        assertEquals(false, isMember)
    }

    @Test
    fun givenLocalConversations_whenGettingConversationsWithoutMetadata_thenShouldReturnsOnlyConversationsWithIncompleteMetadataTrue() =
        runTest {
            val conversationEntity1 = TestStubs.conversationEntity1
            val conversationEntity2 = TestStubs.conversationEntity2
            val user1 = TestStubs.user1
            val user2 = TestStubs.user2
            val member1 = TestStubs.member1
            val member2 = TestStubs.member2

            conversationDAO.insertConversation(conversationEntity1)
            conversationDAO.insertConversation(conversationEntity2.copy(hasIncompleteMetadata = true))

            userDAO.insertUser(user1)
            userDAO.insertUser(user2)

            memberDAO.insertMember(member1, conversationEntity1.id)
            memberDAO.insertMember(member2, conversationEntity1.id)

            conversationDAO.getConversationsWithoutMetadata().let {
                assertEquals(1, it.size)
                assertEquals(conversationEntity2.id, it.first())
            }
        }

    @Test
    fun givenConversation_whenPersistingMembersWithoutMetadata_ThenUsersShouldBeMarkedWithIncompleteMetadataTrue() = runTest(dispatcher) {
        val conversationEntity1 = TestStubs.conversationEntity1
        val user1 = TestStubs.user1

        // given
        conversationDAO.insertConversation(conversationEntity1)

        // when
        memberDAO.insertMembersWithQualifiedId(
            listOf(MemberEntity(user1.id, MemberEntity.Role.Member)),
            conversationEntity1.id
        )

        // then
        val member = userDAO.getUserByQualifiedID(user1.id).first()
        assertEquals(true, member?.hasIncompleteMetadata)
    }

    @Test
    fun givenMembersWithSameDomainInConversation_WhenGetMemberIdsByTheSameDomainInConversation_ThenReturnListOfQualifiedIDs() =
        runTest(dispatcher) {
            // given
            val conversation = TestStubs.conversationEntity1
            val conversationID = conversation.id
            val otherDomain = "anta.com"
            val domain = conversation.id.domain
            val user1 = TestStubs.user1
            val user2 = TestStubs.user2
            val user3 = TestStubs.user3.copy(id = QualifiedIDEntity("3", otherDomain))

            userDAO.insertUser(user1)
            userDAO.insertUser(user2)
            userDAO.insertUser(user3)

            conversationDAO.insertConversation(conversation)

            memberDAO.insertMember(TestStubs.member1, conversationID)
            memberDAO.insertMember(TestStubs.member2, conversationID)
            memberDAO.insertMember(TestStubs.member3.copy(user = QualifiedIDEntity("3", otherDomain)), conversationID)

            // when
            val result = memberDAO.getMemberIdsByTheSameDomainInConversation(domain, conversationID)

            // then
            assertEquals(2, result.size)
            assertTrue(result.contains(user1.id))
            assertTrue(result.contains(user2.id))
        }

}

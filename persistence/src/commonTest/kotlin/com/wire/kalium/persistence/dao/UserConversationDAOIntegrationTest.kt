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

package com.wire.kalium.persistence.dao

import app.cash.turbine.test
import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.member.MemberDAO
import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserConversationDAOIntegrationTest : BaseDatabaseTest() {

    private val user1 = newUserEntity(id = "1")
    private val user2 = newUserEntity(id = "2")

    private val conversationEntity1 = newConversationEntity()

    private val member1 = MemberEntity(user1.id, MemberEntity.Role.Admin)

    private lateinit var conversationDAO: ConversationDAO
    private lateinit var userDAO: UserDAO
    private lateinit var memberDAO: MemberDAO
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, true)
        conversationDAO = db.conversationDAO
        userDAO = db.userDAO
        memberDAO = db.memberDAO
    }

    @Test
    fun givenUserExists_whenInsertingMember_thenOriginalUserDetailsAreKept() = runTest(dispatcher) {
        userDAO.upsertUser(user1)

        conversationDAO.insertConversation(conversationEntity1)
        memberDAO.insertMember(member1, conversationEntity1.id)

        val result = userDAO.observeUserDetailsByQualifiedID(user1.id).first()
        assertEquals(user1, result?.toSimpleEntity())
    }

    @Test
    fun givenTheUserIsPartOfConversation_WHenGettingUsersNotPartOfConversation_ThenReturnUsersWithoutTheConversationMember() = runTest {
        // given
        val userThatIsPartOfConversation = newUserEntity(QualifiedIDEntity("3", "someDomain"))

        val allUsers = listOf(user1, user2, userThatIsPartOfConversation)
        userDAO.upsertUsers(allUsers)

        val conversationId = QualifiedIDEntity(value = "someValue", domain = "someDomain")

        createTestConversation(
            conversationId, listOf(
                MemberEntity(
                    user = QualifiedIDEntity(
                        "3", "someDomain"
                    ), role = MemberEntity.Role.Admin
                )
            )
        )

        // when
        userDAO.observeUsersDetailsNotInConversation(conversationId).test {
            val result = awaitItem()
            // then
            assertEquals((allUsers - userThatIsPartOfConversation), result.map { it.toSimpleEntity() })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenAllTheUserArePartOfConversation_WHenGettingUsersNotPartOfConversation_ThenReturnEmptyResult() = runTest {
        // given
        userDAO.upsertUsers(listOf(user1, user2))

        val conversationId = QualifiedIDEntity(value = "someValue", domain = "someDomain")

        createTestConversation(
            conversationId, listOf(
                MemberEntity(
                    user = user1.id, role = MemberEntity.Role.Admin
                ),
                MemberEntity(
                    user = user2.id, role = MemberEntity.Role.Member
                ),
            )
        )

        // when

        userDAO.observeUsersDetailsNotInConversation(conversationId).test {
            // then
            val result = awaitItem()
            assertTrue { result.isEmpty() }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenConversationHasNoMembers_WhenGettingUsersNotPartOfConversation_ThenReturnAllTheUsers() = runTest {
        // given
        userDAO.upsertUsers(listOf(user1, user2))

        val conversationId = QualifiedIDEntity(value = "someValue", domain = "someDomain")

        createTestConversation(conversationId, emptyList())

        // when
        userDAO.observeUsersDetailsNotInConversation(conversationId).test {
            // then
            val result = awaitItem()
            assertEquals(listOf(user1, user2), result.map { it.toSimpleEntity() })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenAUserAndConversationMembers_whenGettingUsersByHandle_ThenReturnUserMatchingTheHandleAndNotInTheConversation() = runTest {
        // given
        val userThatIsPartOfConversation = newUserEntity(QualifiedIDEntity("3", "someDomain")).copy(handle = "handleMatch")

        val allUsers = listOf(
            user1.copy(handle = "handleMatch"),
            user2.copy(handle = "handleMatch"),
            userThatIsPartOfConversation
        )

        userDAO.upsertUsers(allUsers)

        val conversationId = QualifiedIDEntity(value = "someValue", domain = "someDomain")

        createTestConversation(
            conversationId, listOf(
                MemberEntity(
                    user = QualifiedIDEntity(
                        "3", "someDomain"
                    ), role = MemberEntity.Role.Admin
                )
            )
        )

        // when

        userDAO.getUsersDetailsNotInConversationByHandle(conversationId, "handleMatch")
            .test {
                // then
                val result = awaitItem()
                assertEquals((allUsers - userThatIsPartOfConversation), result.map { it.toSimpleEntity() })
                cancelAndIgnoreRemainingEvents()
            }
    }

    @Test
    fun givenAUserAndConversationMembers_whenGettingUsersByEmail_ThenReturnUserMatchingTheEmailAndNotInTheConversation() = runTest {
        // given
        val userThatIsPartOfConversation = newUserEntity(QualifiedIDEntity("3", "someDomain")).copy(email = "emailMatch")

        val allUsers = listOf(
            user1.copy(email = "emailMatch"),
            user2.copy(email = "emailMatch"),
            userThatIsPartOfConversation
        )

        userDAO.upsertUsers(allUsers)

        val conversationId = QualifiedIDEntity(value = "someValue", domain = "someDomain")

        createTestConversation(
            conversationId, listOf(
                MemberEntity(
                    user = QualifiedIDEntity(
                        "3", "someDomain"
                    ), role = MemberEntity.Role.Admin
                )
            )
        )

        // when

        userDAO.getUsersDetailsNotInConversationByNameOrHandleOrEmail(conversationId, "emailMatch")
            .test {
                // then
                val result = awaitItem()
                assertEquals((allUsers - userThatIsPartOfConversation), result.map { it.toSimpleEntity() })
                cancelAndIgnoreRemainingEvents()
            }
    }

    @Test
    fun givenAUserAndConversationMembers_whenGettingUsersByName_ThenReturnUserMatchingTheEmailAndNotInTheConversation() = runTest {
        // given
        val userThatIsPartOfConversation = newUserEntity(QualifiedIDEntity("3", "someDomain")).copy(name = "nameMatch")

        val allUsers = listOf(
            user1.copy(name = "nameMatch"),
            user2.copy(name = "nameMatch"),
            userThatIsPartOfConversation
        )

        userDAO.upsertUsers(allUsers)

        val conversationId = QualifiedIDEntity(value = "someValue", domain = "someDomain")

        createTestConversation(
            conversationId, listOf(
                MemberEntity(
                    user = QualifiedIDEntity(
                        "3", "someDomain"
                    ), role = MemberEntity.Role.Admin
                )
            )
        )

        // when

        userDAO.getUsersDetailsNotInConversationByNameOrHandleOrEmail(conversationId, "nameMatch")
            .test {
                // then
                val result = awaitItem()
                assertEquals((allUsers - userThatIsPartOfConversation), result.map { it.toSimpleEntity() })
                cancelAndIgnoreRemainingEvents()
            }
    }

    @Test
    fun givenActiveOneOnOneWasSetForConversation_whenFetchingConversationView_thenActiveOneOnOneShouldMatch() = runTest {
        userDAO.upsertUser(user1)
        conversationDAO.insertConversation(conversationEntity1)
        memberDAO.insertMember(member1, conversationEntity1.id)

        userDAO.updateActiveOneOnOneConversation(user1.id, conversationEntity1.id)

        val result = conversationDAO.getConversationByQualifiedID(conversationEntity1.id)
        assertEquals(conversationEntity1.id, result?.userActiveOneOnOneConversationId)
    }

    @Test
    fun givenActiveOneOnOneWasNotSetForConversation_whenFetchingConversationView_thenActiveOneOnOneShouldBeNull() = runTest {
        userDAO.upsertUser(user1)
        conversationDAO.insertConversation(conversationEntity1)
        memberDAO.insertMember(member1, conversationEntity1.id)

        val result = conversationDAO.getConversationByQualifiedID(conversationEntity1.id)
        assertNotNull(result)
        assertNull(result.userActiveOneOnOneConversationId)
    }

    private suspend fun createTestConversation(conversationIDEntity: QualifiedIDEntity, members: List<MemberEntity>) {
        conversationDAO.insertConversation(
            newConversationEntity(conversationIDEntity)
        )

        memberDAO.insertMembersWithQualifiedId(
            memberList = members, conversationID = conversationIDEntity
        )
    }
}

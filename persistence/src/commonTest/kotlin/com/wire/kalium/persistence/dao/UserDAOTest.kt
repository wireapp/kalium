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
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.utils.stubs.TestStubs
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newUserDetailsEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserDAOTest : BaseDatabaseTest() {

    private val user1 = newUserEntity(id = "1")
    private val user2 = newUserEntity(id = "2")
    private val user3 = newUserEntity(id = "3")

    lateinit var db: UserDatabaseBuilder
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        db = createDatabase(selfUserId, encryptedDBSecret, true)
    }

    @Test
    fun givenUser_whenUpdatingProfileAvatar_thenChangesAreEmittedCorrectly() = runTest(dispatcher) {
        //given
        val updatedUser = PartialUserEntity(
            id = user1.id,
            name = "newName",
            handle = user1.handle,
            email = user1.email,
            accentId = user1.accentId,
            previewAssetId = UserAssetIdEntity(
                value ="newAvatar",
                domain = "newAvatarDomain"
            ),
            completeAssetId = UserAssetIdEntity(
                value ="newAvatar",
                domain = "newAvatarDomain"
            ),
            supportedProtocols = user1.supportedProtocols
        )

        db.userDAO.upsertUser(user1)

        db.userDAO.observeUserDetailsByQualifiedID(user1.id).test {
            assertEquals(user1, (awaitItem() as UserDetailsEntity).toSimpleEntity())

            // when
            db.userDAO.updateUser(updatedUser)

            // then
            val newItem = (awaitItem() as UserDetailsEntity)
            assertEquals(updatedUser.previewAssetId, newItem.previewAssetId)
            assertEquals(updatedUser.completeAssetId, newItem.completeAssetId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenUser_ThenUserCanBeInserted() = runTest(dispatcher) {
        db.userDAO.upsertUser(user1)
        val result = db.userDAO.observeUserDetailsByQualifiedID(user1.id).first()
        assertEquals(result?.toSimpleEntity(), user1)
    }

    @Test
    fun givenListOfUsers_ThenMultipleUsersCanBeInsertedAtOnce() = runTest(dispatcher) {
        db.userDAO.upsertUsers(listOf(user1, user2, user3))
        val result1 = db.userDAO.observeUserDetailsByQualifiedID(user1.id).first()
        val result2 = db.userDAO.observeUserDetailsByQualifiedID(user2.id).first()
        val result3 = db.userDAO.observeUserDetailsByQualifiedID(user3.id).first()
        assertEquals(result1?.toSimpleEntity(), user1)
        assertEquals(result2?.toSimpleEntity(), user2)
        assertEquals(result3?.toSimpleEntity(), user3)
    }

    @Test
    fun givenExistingUser_ThenUserCanBeDeleted() = runTest(dispatcher) {
        db.userDAO.upsertUser(user1)
        db.userDAO.deleteUserByQualifiedID(user1.id)
        val result = db.userDAO.observeUserDetailsByQualifiedID(user1.id).first()
        assertNull(result)
    }

    @Test
    fun givenExistingUser_ThenUserCanBeUpdated() = runTest(dispatcher) {
        db.userDAO.upsertUser(user1)
        val updatedUser1 = newUserEntity(user1.id).copy(
            name = "John Doe",
            handle = "johndoe",
            email = "email1",
            phone = "phone1",
            accentId = 1
        )
        db.userDAO.upsertUser(updatedUser1)
        val result = db.userDAO.observeUserDetailsByQualifiedID(user1.id).first()
        assertEquals(result?.toSimpleEntity(), updatedUser1)
    }

    @Test
    fun givenRetrievedUser_ThenUpdatesArePropagatedThroughFlow() = runTest(dispatcher) {
        val collectedValues = mutableListOf<UserEntity?>()

        db.userDAO.upsertUser(user1)

        val updatedUser1 = newUserEntity(user1.id).copy(
            name = "John Doe",
            handle = "johndoe",
            email = "email1",
            phone = "phone1",
            accentId = 1
        )

        db.userDAO.observeUserDetailsByQualifiedID(user1.id).take(2).collect {
            collectedValues.add(it?.toSimpleEntity())
            if (collectedValues.size == 1) {
                db.userDAO.upsertUser(updatedUser1)
            }
        }
        assertEquals(user1, collectedValues[0])
        assertEquals(updatedUser1, collectedValues[1])
    }

    @Test
    fun givenExistingUser_WhenUpdateUserHandle_ThenUserHandleIsUpdated() = runTest(dispatcher) {
        // given
        db.userDAO.upsertUser(user1)
        val updatedHandle = "new-handle"

        // when
        db.userDAO.updateUserHandle(user1.id, updatedHandle)

        // then
        val result = db.userDAO.observeUserDetailsByQualifiedID(user1.id).first()
        assertEquals(updatedHandle, result?.handle)
    }

    @Test
    fun givenNonExistingUser_WhenUpdateUserHandle_ThenNoChanges() = runTest(dispatcher) {
        // given
        val nonExistingQualifiedID = QualifiedIDEntity("non-existing-value", "non-existing-domain")
        val updatedHandle = "new-handle"

        // when
        db.userDAO.updateUserHandle(nonExistingQualifiedID, updatedHandle)

        // then
        val result = db.userDAO.observeUserDetailsByQualifiedID(nonExistingQualifiedID).first()
        assertNull(result)
    }

    @Test
    fun givenAExistingUsers_WhenQueriedUserByUserEmail_ThenResultsIsEqualToThatUser() = runTest(dispatcher) {
        // given
        val user1 = USER_ENTITY_1
        val user2 = USER_ENTITY_2.copy(email = "uniqueEmailForUser2")
        val user3 = USER_ENTITY_3
        db.userDAO.upsertUsers(listOf(user1, user2, user3))
        // when

        db.userDAO.getUserDetailsByNameOrHandleOrEmailAndConnectionStates(
            user2.email!!,
            listOf(ConnectionEntity.State.ACCEPTED)
        ).test {
            // then
            val searchResult = awaitItem()
            assertEquals(searchResult.map { it.toSimpleEntity() }, listOf(user2))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenAExistingUsers_WhenQueriedUserByName_ThenResultsIsEqualToThatUser(): TestResult {
        return runTest(dispatcher) {
            // given
            val user1 = USER_ENTITY_1
            val user2 = USER_ENTITY_3
            val user3 = USER_ENTITY_3.copy(handle = "uniqueHandlForUser3")
            db.userDAO.upsertUsers(listOf(user1, user2, user3))
            // when

            db.userDAO.getUserDetailsByNameOrHandleOrEmailAndConnectionStates(
                user3.handle!!,
                listOf(ConnectionEntity.State.ACCEPTED)
            )
                .test {
                    // then
                    val searchResult = awaitItem()
                    assertEquals(searchResult.map { it.toSimpleEntity() }, listOf(user3))
                    cancelAndIgnoreRemainingEvents()
                }
        }
    }

    @Test
    fun givenAExistingUsers_WhenQueriedUserByHandle_ThenResultsIsEqualToThatUser() = runTest(dispatcher) {
        // given
        val user1 = USER_ENTITY_1.copy(name = "uniqueNameFor User1")
        val user2 = USER_ENTITY_2
        val user3 = USER_ENTITY_3
        db.userDAO.upsertUsers(listOf(user1, user2, user3))
        // when

        db.userDAO.getUserDetailsByNameOrHandleOrEmailAndConnectionStates(user1.name!!, listOf(ConnectionEntity.State.ACCEPTED))
            .test {
                val searchResult = awaitItem()
                assertEquals(searchResult.map { it.toSimpleEntity() }, listOf(user1))
                cancelAndIgnoreRemainingEvents()
            }
    }

    @Test
    fun givenAExistingUsersWithCommonEmailPrefix_WhenQueriedWithThatEmailPrefix_ThenResultIsEqualToTheUsersWithCommonEmailPrefix() =
        runTest(dispatcher) {
            // given
            val commonEmailPrefix = "commonEmail"

            val commonEmailUsers = listOf(
                USER_ENTITY_1.copy(email = commonEmailPrefix + "u1@example.org"),
                USER_ENTITY_2.copy(email = commonEmailPrefix + "u2@example.org"),
                USER_ENTITY_3.copy(email = commonEmailPrefix + "u3@example.org")
            )
            val notCommonEmailUsers = listOf(
                newUserEntity(QualifiedIDEntity("4", "wire.com"))
                    .copy(
                        email = "someDifferentEmail1@wire.com",
                    ),
                newUserEntity(QualifiedIDEntity("5", "wire.com"))
                    .copy(
                        email = "someDifferentEmail2@wire.com"
                    )
            )
            val mockUsers = commonEmailUsers + notCommonEmailUsers

            db.userDAO.upsertUsers(mockUsers)
            // when

            db.userDAO.getUserDetailsByNameOrHandleOrEmailAndConnectionStates(commonEmailPrefix, listOf(ConnectionEntity.State.ACCEPTED))
                .test {
                    // then
                    val searchResult = awaitItem()
                    assertEquals(searchResult.map { it.toSimpleEntity() }, commonEmailUsers)
                    cancelAndIgnoreRemainingEvents()
                }
        }

    // when entering
    @Test
    fun givenAExistingUsers_WhenQueriedWithNonExistingEmail_ThenReturnNoResults() = runTest(dispatcher) {
        // given
        val mockUsers = listOf(USER_ENTITY_1, USER_ENTITY_2, USER_ENTITY_3)
        db.userDAO.upsertUsers(mockUsers)

        val nonExistingEmailQuery = "doesnotexist@wire.com"
        // when

        db.userDAO.getUserDetailsByNameOrHandleOrEmailAndConnectionStates(
            nonExistingEmailQuery,
            listOf(ConnectionEntity.State.ACCEPTED)
        ).test {
            // then
            val searchResult = awaitItem()
            assertTrue { searchResult.isEmpty() }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenAExistingUsers_whenQueriedWithCommonEmailPrefix_ThenResultsUsersEmailContainsThatPrefix() = runTest(dispatcher) {
        // given
        val commonEmailPrefix = "commonEmail"

        val mockUsers = listOf(USER_ENTITY_1, USER_ENTITY_2, USER_ENTITY_3)
        db.userDAO.upsertUsers(mockUsers)
        // when

        db.userDAO.getUserDetailsByNameOrHandleOrEmailAndConnectionStates(
            commonEmailPrefix,
            listOf(ConnectionEntity.State.ACCEPTED)
        ).test {
            // then
            val searchResult = awaitItem()
            searchResult.forEach { userEntity ->
                assertContains(userEntity.email!!, commonEmailPrefix)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenAExistingUsers_whenQueriedWithCommonHandlePrefix_ThenResultsUsersHandleContainsThatPrefix() = runTest(dispatcher) {
        // given
        val commonHandlePrefix = "commonHandle"

        val mockUsers = listOf(USER_ENTITY_1, USER_ENTITY_2, USER_ENTITY_3)
        db.userDAO.upsertUsers(mockUsers)
        // when

        db.userDAO.getUserDetailsByNameOrHandleOrEmailAndConnectionStates(
            commonHandlePrefix,
            listOf(ConnectionEntity.State.ACCEPTED)
        ).test {
            // then
            val searchResult = awaitItem()
            searchResult.forEach { userEntity ->
                assertContains(userEntity.handle!!, commonHandlePrefix)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenAExistingUsers_whenQueriedWithCommonNamePrefix_ThenResultsUsersNameContainsThatPrefix() = runTest(dispatcher) {
        // given
        val commonNamePrefix = "commonName"

        val mockUsers = listOf(USER_ENTITY_1, USER_ENTITY_2, USER_ENTITY_3)
        db.userDAO.upsertUsers(mockUsers)
        // when

        db.userDAO.getUserDetailsByNameOrHandleOrEmailAndConnectionStates(
            commonNamePrefix,
            listOf(ConnectionEntity.State.ACCEPTED)
        ).test {
            // then
            val searchResult = awaitItem()
            searchResult.forEach { userEntity ->
                assertContains(userEntity.name!!, commonNamePrefix)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenAExistingUsers_whenQueriedWithCommonPrefixForNameHandleAndEmail_ThenResultsUsersNameHandleAndEmailContainsThatPrefix() =
        runTest(dispatcher) {
            // given
            val commonPrefix = "common"

            val mockUsers = listOf(
                USER_ENTITY_1.copy(name = commonPrefix + "u1"),
                USER_ENTITY_2.copy(handle = commonPrefix + "u2"),
                USER_ENTITY_3.copy(email = commonPrefix + "u3")
            )
            db.userDAO.upsertUsers(mockUsers)
            // when

            db.userDAO.getUserDetailsByNameOrHandleOrEmailAndConnectionStates(commonPrefix, listOf(ConnectionEntity.State.ACCEPTED))
                .test {
                    // then
                    val searchResult = awaitItem()
                    assertEquals(mockUsers, searchResult.map { it.toSimpleEntity() })
                    cancelAndIgnoreRemainingEvents()
                }
        }

    @Test
    fun givenAExistingUsers_whenQueried_ThenResultsUsersAreConnected() = runTest(dispatcher) {
        // given
        val commonPrefix = "common"

        val mockUsers = listOf(
            USER_ENTITY_1.copy(name = commonPrefix + "u1"),
            USER_ENTITY_2.copy(handle = commonPrefix + "u2"),
            USER_ENTITY_3.copy(email = commonPrefix + "u3")
        )

        db.userDAO.upsertUsers(mockUsers)
        // when

        db.userDAO.getUserDetailsByNameOrHandleOrEmailAndConnectionStates(
            commonPrefix,
            listOf(ConnectionEntity.State.ACCEPTED)
        ).test {
            // then
            val searchResult = awaitItem()
            searchResult.forEach { userEntity ->
                assertEquals(ConnectionEntity.State.ACCEPTED, userEntity.connectionStatus)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenAConnectedExistingUsersAndNonConnected_whenQueried_ThenResultsUsersAreConnected() = runTest(dispatcher) {
        // given
        val commonPrefix = "common"

        val mockUsers = listOf(
            USER_ENTITY_1.copy(name = commonPrefix + "u1"),
            USER_ENTITY_2.copy(handle = commonPrefix + "u2"),
            USER_ENTITY_3.copy(email = commonPrefix + "u3", connectionStatus = ConnectionEntity.State.NOT_CONNECTED),
            USER_ENTITY_4.copy(email = commonPrefix + "u4", connectionStatus = ConnectionEntity.State.NOT_CONNECTED)
        )

        db.userDAO.upsertUsers(mockUsers)
        // when

        db.userDAO.getUserDetailsByNameOrHandleOrEmailAndConnectionStates(commonPrefix, listOf(ConnectionEntity.State.ACCEPTED))
            .test {
                // then
                val searchResult = awaitItem()
                assertTrue(searchResult.size == 2)
                searchResult.forEach { userEntity ->
                    assertEquals(ConnectionEntity.State.ACCEPTED, userEntity.connectionStatus)
                }
                cancelAndIgnoreRemainingEvents()
            }
    }

    @Test
    fun givenAConnectedExistingUserAndNonConnected_whenQueried_ThenResultIsTheConnectedUser() = runTest(dispatcher) {
        // given
        val commonPrefix = "common"

        val expectedResult = listOf(USER_ENTITY_1.copy(name = commonPrefix + "u1"))

        val mockUsers = listOf(
            USER_ENTITY_1.copy(name = commonPrefix + "u1"),
            USER_ENTITY_2.copy(handle = commonPrefix + "u2", connectionStatus = ConnectionEntity.State.NOT_CONNECTED),
            USER_ENTITY_3.copy(name = commonPrefix + "u3", connectionStatus = ConnectionEntity.State.NOT_CONNECTED),
            USER_ENTITY_4.copy(email = commonPrefix + "u4", connectionStatus = ConnectionEntity.State.NOT_CONNECTED)
        )

        db.userDAO.upsertUsers(mockUsers)
        // when

        db.userDAO.getUserDetailsByNameOrHandleOrEmailAndConnectionStates(
            commonPrefix,
            listOf(ConnectionEntity.State.ACCEPTED)
        ).test {
            // then
            val searchResult = awaitItem()
            assertEquals(expectedResult, searchResult.map { it.toSimpleEntity() })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenTheListOfUser_whenQueriedByHandle_ThenResultContainsOnlyTheUserHavingTheHandleAndAreConnected() = runTest(dispatcher) {
        val expectedResult = listOf(
            USER_ENTITY_1.copy(handle = "@someHandle"),
            USER_ENTITY_4.copy(handle = "@someHandle1")
        )

        val mockUsers = listOf(
            USER_ENTITY_1.copy(handle = "@someHandle"),
            USER_ENTITY_2.copy(connectionStatus = ConnectionEntity.State.NOT_CONNECTED),
            USER_ENTITY_3.copy(connectionStatus = ConnectionEntity.State.NOT_CONNECTED),
            USER_ENTITY_4.copy(handle = "@someHandle1")
        )

        db.userDAO.upsertUsers(mockUsers)

        // when

        db.userDAO.getUserDetailsByHandleAndConnectionStates(
            "some",
            listOf(ConnectionEntity.State.ACCEPTED)
        ).test {
            // then
            val searchResult = awaitItem()
            assertEquals(expectedResult, searchResult.map { it.toSimpleEntity() })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenDeletedUser_whenInserting_thenDoNotOverrideOldData() = runTest(dispatcher) {
        // given
        val commonPrefix = "common"

        val mockUser = USER_ENTITY_1.copy(name = commonPrefix + "u1", email = "test@wire.com")
        db.userDAO.upsertUser(mockUser)

        val deletedUser = mockUser.copy(name = null, deleted = true, email = null)
        db.userDAO.upsertUser(deletedUser)

        // when
        db.userDAO.observeUserDetailsByQualifiedID(USER_ENTITY_1.id).first().also { searchResult ->
            // then
            assertEquals(mockUser.copy(deleted = true, userType = UserTypeEntity.NONE), searchResult?.toSimpleEntity())
        }
    }

    @Test
    fun givenAExistingUsers_whenUpdatingTheirValues_ThenResultsIsEqualToThatUserButWithFieldsModified() = runTest(dispatcher) {
        // given
        val newNameA = "new user naming a"
        val newNameB = "new user naming b"
        db.userDAO.upsertUsers(listOf(user1, user3))
        // when
        val updatedUser1 = user1.copy(name = newNameA)
        val updatedUser3 = user3.copy(name = newNameB)
        db.userDAO.upsertUsers(listOf(updatedUser1, updatedUser3))
        // then
        val updated1 = db.userDAO.observeUserDetailsByQualifiedID(updatedUser1.id)
        val updated3 = db.userDAO.observeUserDetailsByQualifiedID(updatedUser3.id)
        assertEquals(newNameA, updated1.first()?.name)
        assertEquals(newNameB, updated3.first()?.name)
    }

    @Test
    fun givenAExistingUsers_whenUpdatingTheirValuesAndRecordNotExists_ThenResultsOneUpdatedAnotherInserted() = runTest(dispatcher) {
        // given
        val newNameA = "new user naming a"
        db.userDAO.upsertUser(user1)
        // when
        val updatedUser1 = user1.copy(name = newNameA)
        db.userDAO.upsertUsers(listOf(updatedUser1, user2))
        // then
        val updated1 = db.userDAO.observeUserDetailsByQualifiedID(updatedUser1.id)
        val inserted2 = db.userDAO.observeUserDetailsByQualifiedID(user2.id)
        assertEquals(newNameA, updated1.first()?.name)
        assertNotNull(inserted2)
    }

    @Test
    fun givenExistingUsers_whenMarkUserAsDeletedAndRemoveFromGroupConv_thenRetainBasicInformation() = runTest {
        val user = newUserEntity().copy(id = UserIDEntity("user-1", "domain-1"))
        val groupConversation =
            newConversationEntity(id = ConversationIDEntity("conversationId", "domain")).copy(type = ConversationEntity.Type.GROUP)
        val oneOnOneConversation =
            newConversationEntity(id = ConversationIDEntity("conversationId1on1", "domain")).copy(type = ConversationEntity.Type.ONE_ON_ONE)
        db.userDAO.upsertUsers(listOf(user))
        db.conversationDAO.insertConversation(groupConversation)
        db.conversationDAO.insertConversation(oneOnOneConversation)
        db.memberDAO.insertMember(MemberEntity(user.id, MemberEntity.Role.Member), groupConversation.id)
        db.memberDAO.insertMember(MemberEntity(user.id, MemberEntity.Role.Member), oneOnOneConversation.id)
        // when
        db.userDAO.markUserAsDeletedAndRemoveFromGroupConv(user.id)

        // then
        db.userDAO.getAllUsersDetails().first().firstOrNull { it.id == user.id }.also {
            assertNotNull(it)
            assertTrue { it.deleted }
            assertEquals(user.name, it.name)
            assertEquals(user.handle, it.handle)
            assertEquals(user.email, it.email)
            assertEquals(user.phone, it.phone)
        }

        db.memberDAO.observeIsUserMember(userId = user.id, conversationId = groupConversation.id).first().also {
            assertFalse(it)
        }

        db.memberDAO.observeIsUserMember(userId = user.id, conversationId = oneOnOneConversation.id).first().also {
            assertTrue(it)
        }
    }

    @Test
    fun givenExistingUsers_whenUpsertToDeleted_thenRetainBasicInformation() = runTest {
        val user = newUserEntity().copy(id = UserIDEntity("user-1", "domain-1"))
        val groupConversation =
            newConversationEntity(id = ConversationIDEntity("conversationId", "domain")).copy(type = ConversationEntity.Type.GROUP)
        val oneOnOneConversation =
            newConversationEntity(id = ConversationIDEntity("conversationId1on1", "domain")).copy(type = ConversationEntity.Type.ONE_ON_ONE)
        db.userDAO.upsertUsers(listOf(user))
        db.conversationDAO.insertConversation(groupConversation)
        db.conversationDAO.insertConversation(oneOnOneConversation)
        db.memberDAO.insertMember(MemberEntity(user.id, MemberEntity.Role.Member), groupConversation.id)
        db.memberDAO.insertMember(MemberEntity(user.id, MemberEntity.Role.Member), oneOnOneConversation.id)
        // when
        db.userDAO.upsertUser(user.copy(deleted = true))

        // then
        db.userDAO.getAllUsersDetails().first().firstOrNull { it.id == user.id }.also {
            assertNotNull(it)
            assertTrue { it.deleted }
            assertEquals(user.name, it.name)
            assertEquals(user.handle, it.handle)
            assertEquals(user.email, it.email)
            assertEquals(user.phone, it.phone)
        }

        db.memberDAO.observeIsUserMember(userId = user.id, conversationId = groupConversation.id).first().also {
            assertFalse(it)
        }

        db.memberDAO.observeIsUserMember(userId = user.id, conversationId = oneOnOneConversation.id).first().also {
            assertTrue(it)
        }
    }

    @Test
    fun givenAExistingUsers_whenUpsertingTeamMembersUserTypes_ThenUserTypeIsUpdated() = runTest(dispatcher) {
        // given
        val newUserType = UserTypeEntity.ADMIN
        db.userDAO.upsertUser(user1)
        // when
        db.userDAO.upsertTeamMemberUserTypes(mapOf(user1.id to newUserType))
        // then
        val updated = db.userDAO.observeUserDetailsByQualifiedID(user1.id)
        assertEquals(newUserType, updated.first()?.userType)
        assertEquals(ConnectionEntity.State.ACCEPTED, updated.first()?.connectionStatus)
    }

    @Test
    fun givenNotExistingUsers_whenUpsertingTeamMembersUserTypes_ThenUserIsInsertedWithCorrectUserType() = runTest(dispatcher) {
        // given
        val newUserType = UserTypeEntity.ADMIN
        // when
        db.userDAO.upsertTeamMemberUserTypes(mapOf(user1.id to newUserType))
        // then
        val inserted = db.userDAO.observeUserDetailsByQualifiedID(user1.id)
        assertEquals(newUserType, inserted.first()?.userType)
        assertEquals(ConnectionEntity.State.ACCEPTED, inserted.first()?.connectionStatus)
    }

    @Test
    fun givenIncompleteTeamMemberInserted_whenUpsert_thenMarkAsComplete() = runTest(dispatcher) {
        // given
        val teamMember = user1.copy(team = "team")
        val conversation = newConversationEntity(id = ConversationIDEntity("conversationId", "domain"))

        db.conversationDAO.insertConversation(conversation)
        db.memberDAO.insertMember(MemberEntity(teamMember.id, MemberEntity.Role.Member), conversation.id)

        // then
        db.userDAO.getAllUsersDetails().first().also {
            assertNotNull(it)
            it.firstOrNull { it.id == teamMember.id }.also {
                assertNotNull(it)
                assertTrue { it.hasIncompleteMetadata }
            }
        }

        // when
        db.userDAO.upsertUsers(listOf(teamMember))

        // then
        db.userDAO.getAllUsersDetails().first().also {
            assertNotNull(it)
            it.firstOrNull { it.id == teamMember.id }.also {
                assertNotNull(it)
                assertFalse { it.hasIncompleteMetadata }
            }
        }
    }

    @Test
    fun givenAExistingUsers_whenUpsertingUsers_ThenResultsOneUpdatedAnotherInsertedWithNoConnectionStatusOverride() = runTest(dispatcher) {
        // given
        val newTeamId = "new team id"
        db.userDAO.upsertUser(user1.copy(connectionStatus = ConnectionEntity.State.ACCEPTED))
        // when
        val updatedUser1 = user1.copy(team = newTeamId)
        db.userDAO.upsertUsers(listOf(updatedUser1, user2))
        // then
        val updated1 = db.userDAO.observeUserDetailsByQualifiedID(updatedUser1.id)
        val inserted2 = db.userDAO.observeUserDetailsByQualifiedID(user2.id)
        assertEquals(newTeamId, updated1.first()?.team)
        assertEquals(ConnectionEntity.State.ACCEPTED, updated1.first()?.connectionStatus)
        assertNotNull(inserted2)
    }

    @Test
    fun givenListOfUsers_WhenGettingListOfUsers_ThenMatchingUsersAreReturned() = runTest(dispatcher) {
        val users = listOf(user1, user2)
        val requestedIds = (users + user3).map { it.id }
        db.userDAO.upsertUsers(users)
        val result = db.userDAO.getUsersDetailsByQualifiedIDList(requestedIds)
        assertEquals(result.map { it.toSimpleEntity() }, users)
        assertTrue(!result.map { it.toSimpleEntity() }.contains(user3))
    }

    @Test
    fun givenUser_WhenMarkingAsDeleted_ThenProperValueShouldBeUpdated() = runTest(dispatcher) {
        val user = user1
        db.userDAO.upsertUser(user)
        val deletedUser = user1.copy(deleted = true, userType = UserTypeEntity.NONE)
        db.userDAO.markUserAsDeletedAndRemoveFromGroupConv(user1.id)
        val result = db.userDAO.observeUserDetailsByQualifiedID(user1.id).first()
        assertEquals(result?.toSimpleEntity(), deletedUser)

    }

    @Test
    fun givenNonExistingUser_whenInsertingOrIgnoringUsers_thenInsertThisUser() = runTest(dispatcher) {
        // given
        val usersToInsert = listOf(user1, user2)
        val expected = listOf(user1, user2)
        // when
        db.userDAO.insertOrIgnoreUsers(usersToInsert)
        // then
        val persistedUsers = db.userDAO.getAllUsersDetails().first()
        assertEquals(expected, persistedUsers.map { it.toSimpleEntity() })
    }

    @Test
    fun givenNonExistingAndExistingUsers_whenInsertingOrIgnoringUsers_thenInsertOnlyNonExistingUsers() = runTest(dispatcher) {
        // given
        val existingUser = user1
        val usersToInsert = listOf(user1.copy(name = "other name to make sure this one wasn't inserted nor edited"), user2)
        val expected = listOf(user1, user2)
        db.userDAO.upsertUser(existingUser)
        // when
        db.userDAO.insertOrIgnoreUsers(usersToInsert)
        // then
        val persistedUsers = db.userDAO.getAllUsersDetails().first()
        assertEquals(expected, persistedUsers.map { it.toSimpleEntity() })
    }

    @Test
    fun givenAnExistingUser_whenUpdatingTheDisplayName_thenTheValueShouldBeUpdated() = runTest(dispatcher) {
        // given
        val expectedNewDisplayName = "new user display name"
        db.userDAO.upsertUser(user1)

        // when
        db.userDAO.updateUserDisplayName(user1.id, expectedNewDisplayName)

        // then
        val persistedUser = db.userDAO.observeUserDetailsByQualifiedID(user1.id).first()
        assertEquals(expectedNewDisplayName, persistedUser?.name)
    }

    @Test
    fun givenExistingUserWithoutMetadata_whenQueryingThem_thenShouldReturnUsersWithoutMetadata() = runTest(dispatcher) {
        // given
        db.userDAO.upsertUser(user1.copy(name = null, handle = null, hasIncompleteMetadata = true))

        // when
        val usersWithoutMetadata = db.userDAO.getUsersDetailsWithoutMetadata()

        // then
        assertEquals(1, usersWithoutMetadata.size)
        assertEquals(user1.id, usersWithoutMetadata.first().id)
    }

    @Test
    fun givenExistingUser_WhenRemoveUserAsset_ThenUserAssetIsRemoved() = runTest(dispatcher) {
        // given
        db.userDAO.upsertUser(user1)
        val assetId = UserAssetIdEntity("asset1", "domain")
        val updatedUser1 = user1.copy(previewAssetId = assetId)

        // when
        db.userDAO.removeUserAsset(assetId)

        // then
        val result = db.userDAO.observeUserDetailsByQualifiedID(user1.id).first()
        assertEquals(result?.toSimpleEntity(), updatedUser1.copy(previewAssetId = null))
    }

    @Test
    fun givenNonExistingUser_WhenRemoveUserAsset_ThenNoChanges() = runTest(dispatcher) {
        // given
        val assetId = UserAssetIdEntity("asset1", "domain")

        // when
        db.userDAO.removeUserAsset(assetId)

        // when
        val result = db.userDAO.observeUserDetailsByQualifiedID(user1.id).first()
        assertNull(result)
    }

    @Test
    fun givenUsersId_whenCallingAllOtherUsers_thenSelfIdIsNotIncluded() = runTest {
        val selfUser = newUserEntity().copy(id = selfUserId)
        val user1 = newUserEntity().copy(id = UserIDEntity("user-1", "domain-1"))
        val user2 = newUserEntity().copy(id = UserIDEntity("user-2", "domain-2"))
        val user3 = newUserEntity().copy(id = UserIDEntity("user-3", "domain-1"))

        db.userDAO.upsertUser(selfUser)
        db.userDAO.upsertUser(user1)
        db.userDAO.upsertUser(user2)
        db.userDAO.upsertUser(user3)

        db.userDAO.allOtherUsersId().also { result ->
            assertFalse {
                result.contains(selfUser.id)
            }
            assertEquals(result, listOf(user1.id, user2.id, user3.id))
        }
    }

    @Test
    fun givenExistingUser_ThenUserCanBeDefederated() = runTest(dispatcher) {
        db.userDAO.upsertUser(user1)
        db.userDAO.markUserAsDefederated(user1.id)
        val result = db.userDAO.observeUserDetailsByQualifiedID(user1.id).first()
        assertNotNull(result)
        assertEquals(true, result.defederated)
    }

    @Test
    fun givenAnExistingUser_whenUpdatingTheSupportedProtocols_thenTheValueShouldBeUpdated() = runTest(dispatcher) {
        // given
        val expectedNewSupportedProtocols = setOf(SupportedProtocolEntity.PROTEUS, SupportedProtocolEntity.MLS)
        db.userDAO.upsertUser(user1)

        // when
        db.userDAO.updateUserSupportedProtocols(user1.id, expectedNewSupportedProtocols)

        // then
        val persistedUser = db.userDAO.observeUserDetailsByQualifiedID(user1.id).first()
        assertEquals(expectedNewSupportedProtocols, persistedUser?.supportedProtocols)
    }

    @Test
    fun givenExistingUserIsDefederated_ThenUserCanBeRefederatedAfterUpdate() = runTest(dispatcher) {
        db.userDAO.upsertUser(user1)
        db.userDAO.markUserAsDefederated(user1.id)
        db.userDAO.upsertUser(user1)
        val result = db.userDAO.observeUserDetailsByQualifiedID(user1.id).first()
        assertNotNull(result)
        assertEquals(false, result.defederated)
    }

    @Test
    fun givenAnExistingUser_WhenUpdatingOneOnOneConversationId_ThenItIsUpdated() = runTest(dispatcher) {
        // given
        val expectedNewOneOnOneConversationId = TestStubs.conversationEntity1.id
        db.userDAO.upsertUser(user1)

        // when
        db.userDAO.updateActiveOneOnOneConversation(user1.id, expectedNewOneOnOneConversationId)

        // then
        val persistedUser = db.userDAO.observeUserDetailsByQualifiedID(user1.id).first()
        assertEquals(expectedNewOneOnOneConversationId, persistedUser?.activeOneOnOneConversationId)
    }

    @Test
    fun givenAnExistingUser_whenPerformingPartialUpdate_thenChangedFieldIsUpdatedOthersAreUnchanged() = runTest(dispatcher) {
        // given
        val expectedName = "new name"
        val update = PartialUserEntity(name = expectedName, id = user1.id)
        db.userDAO.upsertUser(user1)

        // when
        db.userDAO.updateUser(update)

        // then
        val persistedUser = db.userDAO.observeUserDetailsByQualifiedID(user1.id).first()
        assertEquals(expectedName, persistedUser?.name)
        assertEquals(user1.handle, persistedUser?.handle)
        assertEquals(user1.email, persistedUser?.email)
        assertEquals(user1.accentId, persistedUser?.accentId)
        assertEquals(user1.previewAssetId, persistedUser?.previewAssetId)
        assertEquals(user1.completeAssetId, persistedUser?.completeAssetId)
        assertEquals(user1.supportedProtocols, persistedUser?.supportedProtocols)
    }

    @Test
    fun givenExistingUser_whenUpsertingIt_thenAllImportantFieldsAreProperlyUpdated() = runTest(dispatcher) {
        val user = user1.copy(
            name = "Name",
            handle = "Handle",
            email = "Email",
            phone = "Phone",
            accentId = 1,
            team = "Team",
            connectionStatus = ConnectionEntity.State.ACCEPTED,
            previewAssetId = UserAssetIdEntity("PreviewAssetId", "PreviewAssetDomain"),
            completeAssetId = UserAssetIdEntity("CompleteAssetId", "CompleteAssetDomain"),
            availabilityStatus = UserAvailabilityStatusEntity.AVAILABLE,
            userType = UserTypeEntity.STANDARD,
            botService = BotIdEntity("BotService", "BotServiceDomain"),
            deleted = false,
            hasIncompleteMetadata = false,
            expiresAt = null,
            defederated = false,
            supportedProtocols = setOf(SupportedProtocolEntity.MLS),
            activeOneOnOneConversationId = null,
        )
        db.userDAO.upsertUser(user)
        val updatedTeamMemberUser = user.copy(
            name = "newName",
            handle = "newHandle",
            email = "newEmail",
            phone = "newPhone",
            accentId = 2,
            team = "newTeam",
            connectionStatus = ConnectionEntity.State.PENDING,
            previewAssetId = UserAssetIdEntity("newPreviewAssetId", "newPreviewAssetDomain"),
            completeAssetId = UserAssetIdEntity("newCompleteAssetId", "newCompleteAssetDomain"),
            availabilityStatus = UserAvailabilityStatusEntity.BUSY,
            userType = UserTypeEntity.EXTERNAL,
            botService = BotIdEntity("newBotService", "newBotServiceDomain"),
            deleted = false,
            hasIncompleteMetadata = true,
            expiresAt = DateTimeUtil.currentInstant(),
            defederated = true,
            supportedProtocols = setOf(SupportedProtocolEntity.PROTEUS),
            activeOneOnOneConversationId = QualifiedIDEntity("newActiveOneOnOneConversationId", "newActiveOneOnOneConversationDomain")
        )
        db.userDAO.upsertUser(updatedTeamMemberUser)
        val result = db.userDAO.getUsersDetailsByQualifiedIDList(listOf(user1.id)).firstOrNull()
        assertNotNull(result)
        assertEquals(updatedTeamMemberUser.name, result.name)
        assertEquals(updatedTeamMemberUser.handle, result.handle)
        assertEquals(updatedTeamMemberUser.email, result.email)
        assertEquals(updatedTeamMemberUser.phone, result.phone)
        assertEquals(updatedTeamMemberUser.accentId, result.accentId)
        assertEquals(updatedTeamMemberUser.team, result.team)
        assertEquals(updatedTeamMemberUser.previewAssetId, result.previewAssetId)
        assertEquals(updatedTeamMemberUser.completeAssetId, result.completeAssetId)
        assertEquals(updatedTeamMemberUser.userType, result.userType)
        assertEquals(updatedTeamMemberUser.botService, result.botService)
        assertEquals(updatedTeamMemberUser.deleted, result.deleted)
        assertEquals(updatedTeamMemberUser.hasIncompleteMetadata, result.hasIncompleteMetadata)
        assertEquals(updatedTeamMemberUser.expiresAt, result.expiresAt)
        assertEquals(updatedTeamMemberUser.supportedProtocols, result.supportedProtocols)
        assertNotEquals(updatedTeamMemberUser.connectionStatus, result.connectionStatus)
        assertNotEquals(updatedTeamMemberUser.availabilityStatus, result.availabilityStatus)
        assertNotEquals(updatedTeamMemberUser.defederated, result.defederated)
        assertNotEquals(updatedTeamMemberUser.activeOneOnOneConversationId, result.activeOneOnOneConversationId)
    }

    @Test
    fun givenListOfUsers_whenOnlyOneBelongsToTheTeam_thenReturnTrue() = runTest {
        val teamId = "teamId"
        val users = listOf(
            newUserEntity().copy(team = teamId, id = UserIDEntity("1", "wire.com")),
            newUserEntity().copy(team = null, id = UserIDEntity("2", "wire.com")),
            newUserEntity().copy(team = null, id = UserIDEntity("3", "wire.com")),
            newUserEntity().copy(team = null, id = UserIDEntity("4", "wire.com")),
            newUserEntity().copy(team = null, id = UserIDEntity("5", "wire.com")),
        )

        db.userDAO.upsertUsers(users)

        assertTrue { db.userDAO.isAtLeastOneUserATeamMember(users.map { it.id }, teamId) }
    }

    @Test
    fun givenListOfUsers_whenNoneBelongsToTheTeam_thenReturnFalse() = runTest {
        val teamId = "teamId"
        val users = listOf(
            newUserEntity().copy(team = null, id = UserIDEntity("1", "wire.com")),
            newUserEntity().copy(team = null, id = UserIDEntity("2", "wire.com")),
            newUserEntity().copy(team = null, id = UserIDEntity("3", "wire.com")),
            newUserEntity().copy(team = null, id = UserIDEntity("4", "wire.com")),
            newUserEntity().copy(team = null, id = UserIDEntity("5", "wire.com")),
        )

        db.userDAO.upsertUsers(users)

        assertFalse { db.userDAO.isAtLeastOneUserATeamMember(users.map { it.id }, teamId) }
    }

    @Test
    fun givenPersistedUser_whenUpsertingTheSameExactUser_thenItShouldIgnoreAndNotNotifyOtherQueries() = runTest(dispatcher) {
        // Given
        val user = newUserEntity()
        val userDetails = newUserDetailsEntity()
        db.userDAO.upsertUser(user)
        val updatedUser = user.copy(name = "new_name")

        db.userDAO.observeUserDetailsByQualifiedID(user.id).test {
            val initialValue = awaitItem()
            assertEquals(userDetails, initialValue)

            // When
            db.userDAO.upsertUser(updatedUser) // the same exact user is being saved again

            // Then
            expectNoEvents() // other query should not be notified
        }
    }

    @Test
    fun givenPersistedUser_whenUpsertingUpdatedUser_thenItShouldBeSavedAndOtherQueriesShouldBeUpdated() = runTest(dispatcher) {
        // Given
        val user = newUserEntity()
        val userDetails = newUserDetailsEntity()
        db.userDAO.upsertUser(user)
        val updatedUser = user.copy(name = "new_name")
        val updatedUserDetails = userDetails.copy(name = "new_name")

        db.userDAO.observeUserDetailsByQualifiedID(user.id).test {
            val initialValue = awaitItem()
            assertEquals(userDetails, initialValue)

            // When
            db.userDAO.upsertUser(updatedUser) // updated user is being saved

            // Then
            val updatedValue = awaitItem() // other query should be notified
            assertEquals(updatedUserDetails, updatedValue)
        }
    }

    private companion object {
        val USER_ENTITY_1 = newUserEntity(QualifiedIDEntity("1", "wire.com"))
        val USER_ENTITY_2 = newUserEntity(QualifiedIDEntity("2", "wire.com"))
        val USER_ENTITY_3 = newUserEntity(QualifiedIDEntity("3", "wire.com"))
        val USER_ENTITY_4 = newUserEntity(QualifiedIDEntity("4", "wire.com"))
    }

}

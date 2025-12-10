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

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectionDaoTest : BaseDatabaseTest() {

    private val connection1 = connectionEntity("1")
    private val connection2 = connectionEntity("2")

    lateinit var db: UserDatabaseBuilder
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        db = createDatabase(selfUserId, encryptedDBSecret, true)
    }

    @Test
    fun givenConnection_ThenConnectionCanBeInserted() = runTest {
        db.connectionDAO.insertConnection(connection1)
        val result = db.connectionDAO.getConnectionRequests().first()
        assertEquals(connection1, result[0])
    }

    @Test
    fun givenConnectionWithoutShouldNotifyFlag_ThenConnectionCanBeInsertedAndDefaultFlagIsUsed() = runTest {
        db.connectionDAO.insertConnection(connection1.copy(shouldNotify = null))
        val result = db.connectionDAO.getConnectionRequests().first()
        assertEquals(connection1, result[0])
    }

    @Test
    fun givenConnection_WhenInsertingAlreadyExistedConnection_ThenShouldNotifyStaysOldOne() = runTest {
        db.connectionDAO.insertConnection(connection1)
        db.connectionDAO.insertConnection(connection1.copy(shouldNotify = false))
        val result = db.connectionDAO.getConnectionRequests().first()
        assertEquals(connection1, result[0])
    }

    @Test
    fun givenConnection_WhenUpdateNotifyFlag_ThenItIsUpdated() = runTest {
        db.connectionDAO.insertConnection(connection1)
        db.connectionDAO.updateNotificationFlag(false, connection1.qualifiedToId)
        val result = db.connectionDAO.getConnectionRequests().first()
        assertEquals(false, result[0].shouldNotify)
    }

    @Test
    fun givenFewConnections_WhenUpdateNotifyFlagForAll_ThenItIsUpdated() = runTest {
        db.connectionDAO.insertConnection(connection1)
        db.connectionDAO.insertConnection(connection2)
        db.connectionDAO.setAllConnectionsAsNotified()
        val result = db.connectionDAO.getConnectionRequests().first()
        assertEquals(false, result[0].shouldNotify)
        assertEquals(false, result[1].shouldNotify)
    }

    @Test
    fun givenConnectionExists_WhenGettingConnection_ThenItIsReturned() = runTest {
        db.connectionDAO.insertConnection(connection1)
        val result = db.connectionDAO.getConnection(connection1.qualifiedConversationId)
        assertEquals(connection1, result)
    }

    @Test
    fun givenConnectionNotExists_WhenGettingConnection_ThenNullIsReturned() = runTest {
        val result = db.connectionDAO.getConnection(connection1.qualifiedConversationId)
        assertEquals(null, result)
    }

    @Test
    fun givenConnectionWithRegularUserType_WhenRetrievingConnection_ThenUserTypeIsMappedToRegular() = runTest {
        val userId = QualifiedIDEntity("user1", "wire.com")
        val user = createUserEntity(userId, UserTypeEntity.STANDARD)
        val connection = connectionEntityWithUser("1", user)

        db.userDAO.upsertUser(user)
        db.connectionDAO.insertConnection(connection)

        val result = db.connectionDAO.getConnectionRequests().first()
        assertEquals(UserTypeEntity.STANDARD, result[0].otherUser?.userType)
    }

    @Test
    fun givenConnectionWithAdminUserType_WhenRetrievingConnection_ThenUserTypeIsMappedToRegular() = runTest {
        val userId = QualifiedIDEntity("user1", "wire.com")
        val user = createUserEntity(userId, UserTypeEntity.ADMIN)
        val connection = connectionEntityWithUser("1", user)

        db.userDAO.upsertUser(user)
        db.connectionDAO.insertConnection(connection)

        val result = db.connectionDAO.getConnectionRequests().first()
        assertEquals(UserTypeEntity.ADMIN, result[0].otherUser?.userType)
    }

    @Test
    fun givenConnectionWithOwnerUserType_WhenRetrievingConnection_ThenUserTypeIsMappedToRegular() = runTest {
        val userId = QualifiedIDEntity("user1", "wire.com")
        val user = createUserEntity(userId, UserTypeEntity.OWNER)
        val connection = connectionEntityWithUser("1", user)

        db.userDAO.upsertUser(user)
        db.connectionDAO.insertConnection(connection)

        val result = db.connectionDAO.getConnectionRequests().first()
        assertEquals(UserTypeEntity.OWNER, result[0].otherUser?.userType)
    }

    @Test
    fun givenConnectionWithExternalUserType_WhenRetrievingConnection_ThenUserTypeIsMappedToRegular() = runTest {
        val userId = QualifiedIDEntity("user1", "wire.com")
        val user = createUserEntity(userId, UserTypeEntity.EXTERNAL)
        val connection = connectionEntityWithUser("1", user)

        db.userDAO.upsertUser(user)
        db.connectionDAO.insertConnection(connection)

        val result = db.connectionDAO.getConnectionRequests().first()
        assertEquals(UserTypeEntity.EXTERNAL, result[0].otherUser?.userType)
    }

    @Test
    fun givenConnectionWithFederatedUserType_WhenRetrievingConnection_ThenUserTypeIsMappedToRegular() = runTest {
        val userId = QualifiedIDEntity("user1", "wire.com")
        val user = createUserEntity(userId, UserTypeEntity.FEDERATED)
        val connection = connectionEntityWithUser("1", user)

        db.userDAO.upsertUser(user)
        db.connectionDAO.insertConnection(connection)

        val result = db.connectionDAO.getConnectionRequests().first()
        assertEquals(UserTypeEntity.FEDERATED, result[0].otherUser?.userType)
    }

    @Test
    fun givenConnectionWithGuestUserType_WhenRetrievingConnection_ThenUserTypeIsMappedToRegular() = runTest {
        val userId = QualifiedIDEntity("user1", "wire.com")
        val user = createUserEntity(userId, UserTypeEntity.GUEST)
        val connection = connectionEntityWithUser("1", user)

        db.userDAO.upsertUser(user)
        db.connectionDAO.insertConnection(connection)

        val result = db.connectionDAO.getConnectionRequests().first()
        assertEquals(UserTypeEntity.GUEST, result[0].otherUser?.userType)
    }

    @Test
    fun givenConnectionWithNoneUserType_WhenRetrievingConnection_ThenUserTypeIsMappedToRegular() = runTest {
        val userId = QualifiedIDEntity("user1", "wire.com")
        val user = createUserEntity(userId, UserTypeEntity.NONE)
        val connection = connectionEntityWithUser("1", user)

        db.userDAO.upsertUser(user)
        db.connectionDAO.insertConnection(connection)

        val result = db.connectionDAO.getConnectionRequests().first()
        assertEquals(UserTypeEntity.NONE, result[0].otherUser?.userType)
    }

    @Test
    fun givenConnectionWithServiceUserType_WhenRetrievingConnection_ThenUserTypeIsMappedToBot() = runTest {
        val userId = QualifiedIDEntity("bot1", "wire.com")
        val user = createUserEntity(userId, UserTypeEntity.SERVICE)
        val connection = connectionEntityWithUser("1", user)

        db.userDAO.upsertUser(user)
        db.connectionDAO.insertConnection(connection)

        val result = db.connectionDAO.getConnectionRequests().first()
        assertEquals(UserTypeEntity.SERVICE, result[0].otherUser?.userType)
    }

    @Test
    fun givenConnectionWithAppUserType_WhenRetrievingConnection_ThenUserTypeIsMappedToApp() = runTest {
        val userId = QualifiedIDEntity("app1", "wire.com")
        val user = createUserEntity(userId, UserTypeEntity.APP)
        val connection = connectionEntityWithUser("1", user)

        db.userDAO.upsertUser(user)
        db.connectionDAO.insertConnection(connection)

        val result = db.connectionDAO.getConnectionRequests().first()
        assertEquals(UserTypeEntity.APP, result[0].otherUser?.userType)
    }

    @Test
    fun givenMultipleConnectionsWithDifferentUserTypes_WhenRetrievingConnections_ThenAllUserTypesAreMappedCorrectly() = runTest {
        val regularUser = createUserEntity(QualifiedIDEntity("regular", "wire.com"), UserTypeEntity.STANDARD)
        val botUser = createUserEntity(QualifiedIDEntity("bot", "wire.com"), UserTypeEntity.SERVICE)
        val appUser = createUserEntity(QualifiedIDEntity("app", "wire.com"), UserTypeEntity.APP)

        val connection1 = connectionEntityWithUser("1", regularUser)
        val connection2 = connectionEntityWithUser("2", botUser)
        val connection3 = connectionEntityWithUser("3", appUser)

        db.userDAO.upsertUser(regularUser)
        db.userDAO.upsertUser(botUser)
        db.userDAO.upsertUser(appUser)

        db.connectionDAO.insertConnection(connection1)
        db.connectionDAO.insertConnection(connection2)
        db.connectionDAO.insertConnection(connection3)

        val result = db.connectionDAO.getConnectionRequests().first()

        assertEquals(3, result.size)
        assertEquals(UserTypeEntity.STANDARD, result[0].otherUser?.userType)
        assertEquals(UserTypeEntity.SERVICE, result[1].otherUser?.userType)
        assertEquals(UserTypeEntity.APP, result[2].otherUser?.userType)
    }

    companion object {
        private val OTHER_USER_ID = QualifiedIDEntity("me", "wire.com")

        private fun connectionEntity(id: String = "0") = ConnectionEntity(
            conversationId = id,
            from = "from_string",
            lastUpdateDate = Instant.parse("2022-03-30T15:36:00.000Z"),
            qualifiedConversationId = QualifiedIDEntity(id, "wire.com"),
            qualifiedToId = OTHER_USER_ID,
            status = ConnectionEntity.State.PENDING,
            toId = OTHER_USER_ID.value,
            shouldNotify = true
        )

        private fun connectionEntityWithUser(id: String, user: UserEntity) = ConnectionEntity(
            conversationId = id,
            from = "from_string",
            lastUpdateDate = Instant.parse("2022-03-30T15:36:00.000Z"),
            qualifiedConversationId = QualifiedIDEntity(id, "wire.com"),
            qualifiedToId = user.id,
            status = ConnectionEntity.State.PENDING,
            toId = user.id.value,
            shouldNotify = true,
            otherUser = user
        )

        private fun createUserEntity(
            id: QualifiedIDEntity,
            userType: UserTypeEntity
        ) = UserEntity(
            id = id,
            name = "Test User",
            handle = "testhandle",
            email = "test@example.com",
            phone = null,
            accentId = 1,
            team = null,
            connectionStatus = ConnectionEntity.State.PENDING,
            previewAssetId = null,
            completeAssetId = null,
            availabilityStatus = UserAvailabilityStatusEntity.NONE,
            userType = userType,
            botService = null,
            deleted = false,
            hasIncompleteMetadata = false,
            expiresAt = null,
            defederated = false,
            supportedProtocols = null,
            activeOneOnOneConversationId = null
        )
    }
}


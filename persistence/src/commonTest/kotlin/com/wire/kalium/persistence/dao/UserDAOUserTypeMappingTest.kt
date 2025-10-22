/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for UserDAO focusing specifically on user type mapping.
 * Verifies that UserTypeEntity values are correctly stored and retrieved from the database.
 */
class UserDAOUserTypeMappingTest : BaseDatabaseTest() {

    lateinit var db: UserDatabaseBuilder
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        db = createDatabase(selfUserId, encryptedDBSecret, true)
    }

    @Test
    fun givenUserWithStandardType_WhenInsertingAndRetrieving_ThenUserTypeIsMappedToRegular() = runTest(dispatcher) {
        // given
        val user = newUserEntity("standard-user").copy(
            userType = UserTypeEntity.STANDARD
        )

        // when
        db.userDAO.upsertUser(user)
        val result = db.userDAO.observeUserDetailsByQualifiedID(user.id).first()

        // then
        assertEquals(UserTypeEntity.STANDARD, result?.userType)
    }

    @Test
    fun givenUserWithAdminType_WhenInsertingAndRetrieving_ThenUserTypeIsMappedToRegular() = runTest(dispatcher) {
        // given
        val user = newUserEntity("admin-user").copy(
            userType = UserTypeEntity.ADMIN
        )

        // when
        db.userDAO.upsertUser(user)
        val result = db.userDAO.observeUserDetailsByQualifiedID(user.id).first()

        // then
        assertEquals(UserTypeEntity.ADMIN, result?.userType)
    }

    @Test
    fun givenUserWithOwnerType_WhenInsertingAndRetrieving_ThenUserTypeIsMappedToRegular() = runTest(dispatcher) {
        // given
        val user = newUserEntity("owner-user").copy(
            userType = UserTypeEntity.OWNER
        )

        // when
        db.userDAO.upsertUser(user)
        val result = db.userDAO.observeUserDetailsByQualifiedID(user.id).first()

        // then
        assertEquals(UserTypeEntity.OWNER, result?.userType)
    }

    @Test
    fun givenUserWithExternalType_WhenInsertingAndRetrieving_ThenUserTypeIsMappedToRegular() = runTest(dispatcher) {
        // given
        val user = newUserEntity("external-user").copy(
            userType = UserTypeEntity.EXTERNAL
        )

        // when
        db.userDAO.upsertUser(user)
        val result = db.userDAO.observeUserDetailsByQualifiedID(user.id).first()

        // then
        assertEquals(UserTypeEntity.EXTERNAL, result?.userType)
    }

    @Test
    fun givenUserWithFederatedType_WhenInsertingAndRetrieving_ThenUserTypeIsMappedToRegular() = runTest(dispatcher) {
        // given
        val user = newUserEntity("federated-user").copy(
            userType = UserTypeEntity.FEDERATED
        )

        // when
        db.userDAO.upsertUser(user)
        val result = db.userDAO.observeUserDetailsByQualifiedID(user.id).first()

        // then
        assertEquals(UserTypeEntity.FEDERATED, result?.userType)
    }

    @Test
    fun givenUserWithGuestType_WhenInsertingAndRetrieving_ThenUserTypeIsMappedToRegular() = runTest(dispatcher) {
        // given
        val user = newUserEntity("guest-user").copy(
            userType = UserTypeEntity.GUEST
        )

        // when
        db.userDAO.upsertUser(user)
        val result = db.userDAO.observeUserDetailsByQualifiedID(user.id).first()

        // then
        assertEquals(UserTypeEntity.GUEST, result?.userType)
    }

    @Test
    fun givenUserWithNoneType_WhenInsertingAndRetrieving_ThenUserTypeIsMappedToRegular() = runTest(dispatcher) {
        // given
        val user = newUserEntity("none-user").copy(
            userType = UserTypeEntity.NONE
        )

        // when
        db.userDAO.upsertUser(user)
        val result = db.userDAO.observeUserDetailsByQualifiedID(user.id).first()

        // then
        assertEquals(UserTypeEntity.NONE, result?.userType)
    }

    @Test
    fun givenUserWithServiceType_WhenInsertingAndRetrieving_ThenUserTypeIsMappedToBot() = runTest(dispatcher) {
        // given
        val user = newUserEntity("bot-user").copy(
            userType = UserTypeEntity.SERVICE
        )

        // when
        db.userDAO.upsertUser(user)
        val result = db.userDAO.observeUserDetailsByQualifiedID(user.id).first()

        // then
        assertEquals(UserTypeEntity.SERVICE, result?.userType)
    }

    @Test
    fun givenUserWithAppType_WhenInsertingAndRetrieving_ThenUserTypeIsMappedToApp() = runTest(dispatcher) {
        // given
        val user = newUserEntity("app-user").copy(
            userType = UserTypeEntity.APP
        )

        // when
        db.userDAO.upsertUser(user)
        val result = db.userDAO.observeUserDetailsByQualifiedID(user.id).first()

        // then
        assertEquals(UserTypeEntity.APP, result?.userType)
    }

    @Test
    fun givenMultipleUsersWithDifferentTypes_WhenRetrieving_ThenAllUserTypesAreMappedCorrectly() = runTest(dispatcher) {
        // given
        val regularUser = newUserEntity("regular").copy(
            userType = UserTypeEntity.STANDARD
        )
        val adminUser = newUserEntity("admin").copy(
            userType = UserTypeEntity.ADMIN
        )
        val botUser = newUserEntity("bot").copy(
            userType = UserTypeEntity.SERVICE
        )
        val appUser = newUserEntity("app").copy(
            userType = UserTypeEntity.APP
        )
        val externalUser = newUserEntity("external").copy(
            userType = UserTypeEntity.EXTERNAL
        )

        // when
        db.userDAO.upsertUsers(listOf(regularUser, adminUser, botUser, appUser, externalUser))

        // then
        val regularResult = db.userDAO.observeUserDetailsByQualifiedID(regularUser.id).first()
        assertEquals(UserTypeEntity.STANDARD, regularResult?.userType)

        val adminResult = db.userDAO.observeUserDetailsByQualifiedID(adminUser.id).first()
        assertEquals(UserTypeEntity.ADMIN, adminResult?.userType)

        val botResult = db.userDAO.observeUserDetailsByQualifiedID(botUser.id).first()
        assertEquals(UserTypeEntity.SERVICE, botResult?.userType)

        val appResult = db.userDAO.observeUserDetailsByQualifiedID(appUser.id).first()
        assertEquals(UserTypeEntity.APP, appResult?.userType)

        val externalResult = db.userDAO.observeUserDetailsByQualifiedID(externalUser.id).first()
        assertEquals(UserTypeEntity.EXTERNAL, externalResult?.userType)
    }

    @Test
    fun givenUserWithBotType_WhenSearchingByNameOrHandle_ThenUserTypeIsPreserved() = runTest(dispatcher) {
        // given
        val botUser = newUserEntity("searchable-bot").copy(
            name = "SearchBot",
            handle = "searchbot",
            userType = UserTypeEntity.SERVICE
        )

        // when
        db.userDAO.upsertUser(botUser)

        // then
        db.userDAO.getUserDetailsByNameOrHandleOrEmailAndConnectionStates(
            "Search",
            listOf(ConnectionEntity.State.ACCEPTED)
        ).test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals(UserTypeEntity.SERVICE, result.first().userType)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenUserWithAppType_WhenSearchingByHandle_ThenUserTypeIsPreserved() = runTest(dispatcher) {
        // given
        val appUser = newUserEntity("searchable-app").copy(
            handle = "myapp",
            userType = UserTypeEntity.APP
        )

        // when
        db.userDAO.upsertUser(appUser)

        // then
        db.userDAO.getUserDetailsByHandleAndConnectionStates(
            "myapp",
            listOf(ConnectionEntity.State.ACCEPTED)
        ).test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals(UserTypeEntity.APP, result.first().userType)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenUserWithRegularType_WhenUpdatingUser_ThenUserTypeIsPreserved() = runTest(dispatcher) {
        // given
        val user = newUserEntity("updatable-user").copy(
            userType = UserTypeEntity.EXTERNAL
        )
        db.userDAO.upsertUser(user)

        // when - update user name but not type
        val updatedPartial = PartialUserEntity(
            id = user.id,
            name = "Updated Name",
            handle = user.handle,
            email = user.email,
            accentId = user.accentId,
            previewAssetId = user.previewAssetId,
            completeAssetId = user.completeAssetId,
            supportedProtocols = user.supportedProtocols
        )
        db.userDAO.updateUser(updatedPartial)

        // then
        val result = db.userDAO.observeUserDetailsByQualifiedID(user.id).first()
        assertEquals(UserTypeEntity.EXTERNAL, result?.userType)
        assertEquals("Updated Name", result?.name)
    }

    @Test
    fun givenUsersWithDifferentTypes_WhenGettingAllUsers_ThenAllTypesAreMappedCorrectly() = runTest(dispatcher) {
        // given
        val users = listOf(
            newUserEntity("user1").copy(userType = UserTypeEntity.STANDARD),
            newUserEntity("user2").copy(userType = UserTypeEntity.ADMIN),
            newUserEntity("user3").copy(userType = UserTypeEntity.SERVICE),
            newUserEntity("user4").copy(userType = UserTypeEntity.APP),
            newUserEntity("user5").copy(userType = UserTypeEntity.GUEST)
        )

        // when
        db.userDAO.upsertUsers(users)
        val allUsers = db.userDAO.getAllUsersDetails().first()

        // then
        assertEquals(5, allUsers.size)
        val userTypesMap = allUsers.associate { it.id.value to it.userType }

        assertEquals(UserTypeEntity.STANDARD, userTypesMap["user1"])
        assertEquals(UserTypeEntity.ADMIN, userTypesMap["user2"])
        assertEquals(UserTypeEntity.SERVICE, userTypesMap["user3"])
        assertEquals(UserTypeEntity.APP, userTypesMap["user4"])
        assertEquals(UserTypeEntity.GUEST, userTypesMap["user5"])
    }
}


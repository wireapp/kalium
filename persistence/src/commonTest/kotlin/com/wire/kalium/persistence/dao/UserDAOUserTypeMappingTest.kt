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
 * Tests for UserDAO focusing specifically on the toUserTypeInfoEntity mapping.
 * Verifies that UserTypeEntity values are correctly mapped to UserTypeInfoEntity
 * when users are inserted and retrieved from the database.
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
            userType = UserTypeInfoEntity.Regular(UserTypeEntity.STANDARD)
        )

        // when
        db.userDAO.upsertUser(user)
        val result = db.userDAO.observeUserDetailsByQualifiedID(user.id).first()

        // then
        assertEquals(UserTypeInfoEntity.Regular(UserTypeEntity.STANDARD), result?.userType)
    }

    @Test
    fun givenUserWithAdminType_WhenInsertingAndRetrieving_ThenUserTypeIsMappedToRegular() = runTest(dispatcher) {
        // given
        val user = newUserEntity("admin-user").copy(
            userType = UserTypeInfoEntity.Regular(UserTypeEntity.ADMIN)
        )

        // when
        db.userDAO.upsertUser(user)
        val result = db.userDAO.observeUserDetailsByQualifiedID(user.id).first()

        // then
        assertEquals(UserTypeInfoEntity.Regular(UserTypeEntity.ADMIN), result?.userType)
    }

    @Test
    fun givenUserWithOwnerType_WhenInsertingAndRetrieving_ThenUserTypeIsMappedToRegular() = runTest(dispatcher) {
        // given
        val user = newUserEntity("owner-user").copy(
            userType = UserTypeInfoEntity.Regular(UserTypeEntity.OWNER)
        )

        // when
        db.userDAO.upsertUser(user)
        val result = db.userDAO.observeUserDetailsByQualifiedID(user.id).first()

        // then
        assertEquals(UserTypeInfoEntity.Regular(UserTypeEntity.OWNER), result?.userType)
    }

    @Test
    fun givenUserWithExternalType_WhenInsertingAndRetrieving_ThenUserTypeIsMappedToRegular() = runTest(dispatcher) {
        // given
        val user = newUserEntity("external-user").copy(
            userType = UserTypeInfoEntity.Regular(UserTypeEntity.EXTERNAL)
        )

        // when
        db.userDAO.upsertUser(user)
        val result = db.userDAO.observeUserDetailsByQualifiedID(user.id).first()

        // then
        assertEquals(UserTypeInfoEntity.Regular(UserTypeEntity.EXTERNAL), result?.userType)
    }

    @Test
    fun givenUserWithFederatedType_WhenInsertingAndRetrieving_ThenUserTypeIsMappedToRegular() = runTest(dispatcher) {
        // given
        val user = newUserEntity("federated-user").copy(
            userType = UserTypeInfoEntity.Regular(UserTypeEntity.FEDERATED)
        )

        // when
        db.userDAO.upsertUser(user)
        val result = db.userDAO.observeUserDetailsByQualifiedID(user.id).first()

        // then
        assertEquals(UserTypeInfoEntity.Regular(UserTypeEntity.FEDERATED), result?.userType)
    }

    @Test
    fun givenUserWithGuestType_WhenInsertingAndRetrieving_ThenUserTypeIsMappedToRegular() = runTest(dispatcher) {
        // given
        val user = newUserEntity("guest-user").copy(
            userType = UserTypeInfoEntity.Regular(UserTypeEntity.GUEST)
        )

        // when
        db.userDAO.upsertUser(user)
        val result = db.userDAO.observeUserDetailsByQualifiedID(user.id).first()

        // then
        assertEquals(UserTypeInfoEntity.Regular(UserTypeEntity.GUEST), result?.userType)
    }

    @Test
    fun givenUserWithNoneType_WhenInsertingAndRetrieving_ThenUserTypeIsMappedToRegular() = runTest(dispatcher) {
        // given
        val user = newUserEntity("none-user").copy(
            userType = UserTypeInfoEntity.Regular(UserTypeEntity.NONE)
        )

        // when
        db.userDAO.upsertUser(user)
        val result = db.userDAO.observeUserDetailsByQualifiedID(user.id).first()

        // then
        assertEquals(UserTypeInfoEntity.Regular(UserTypeEntity.NONE), result?.userType)
    }

    @Test
    fun givenUserWithServiceType_WhenInsertingAndRetrieving_ThenUserTypeIsMappedToBot() = runTest(dispatcher) {
        // given
        val user = newUserEntity("bot-user").copy(
            userType = UserTypeInfoEntity.Bot
        )

        // when
        db.userDAO.upsertUser(user)
        val result = db.userDAO.observeUserDetailsByQualifiedID(user.id).first()

        // then
        assertEquals(UserTypeInfoEntity.Bot, result?.userType)
    }

    @Test
    fun givenUserWithAppType_WhenInsertingAndRetrieving_ThenUserTypeIsMappedToApp() = runTest(dispatcher) {
        // given
        val user = newUserEntity("app-user").copy(
            userType = UserTypeInfoEntity.App
        )

        // when
        db.userDAO.upsertUser(user)
        val result = db.userDAO.observeUserDetailsByQualifiedID(user.id).first()

        // then
        assertEquals(UserTypeInfoEntity.App, result?.userType)
    }

    @Test
    fun givenMultipleUsersWithDifferentTypes_WhenRetrieving_ThenAllUserTypesAreMappedCorrectly() = runTest(dispatcher) {
        // given
        val regularUser = newUserEntity("regular").copy(
            userType = UserTypeInfoEntity.Regular(UserTypeEntity.STANDARD)
        )
        val adminUser = newUserEntity("admin").copy(
            userType = UserTypeInfoEntity.Regular(UserTypeEntity.ADMIN)
        )
        val botUser = newUserEntity("bot").copy(
            userType = UserTypeInfoEntity.Bot
        )
        val appUser = newUserEntity("app").copy(
            userType = UserTypeInfoEntity.App
        )
        val externalUser = newUserEntity("external").copy(
            userType = UserTypeInfoEntity.Regular(UserTypeEntity.EXTERNAL)
        )

        // when
        db.userDAO.upsertUsers(listOf(regularUser, adminUser, botUser, appUser, externalUser))

        // then
        val regularResult = db.userDAO.observeUserDetailsByQualifiedID(regularUser.id).first()
        assertEquals(UserTypeInfoEntity.Regular(UserTypeEntity.STANDARD), regularResult?.userType)

        val adminResult = db.userDAO.observeUserDetailsByQualifiedID(adminUser.id).first()
        assertEquals(UserTypeInfoEntity.Regular(UserTypeEntity.ADMIN), adminResult?.userType)

        val botResult = db.userDAO.observeUserDetailsByQualifiedID(botUser.id).first()
        assertEquals(UserTypeInfoEntity.Bot, botResult?.userType)

        val appResult = db.userDAO.observeUserDetailsByQualifiedID(appUser.id).first()
        assertEquals(UserTypeInfoEntity.App, appResult?.userType)

        val externalResult = db.userDAO.observeUserDetailsByQualifiedID(externalUser.id).first()
        assertEquals(UserTypeInfoEntity.Regular(UserTypeEntity.EXTERNAL), externalResult?.userType)
    }

    @Test
    fun givenUserWithBotType_WhenSearchingByNameOrHandle_ThenUserTypeIsPreserved() = runTest(dispatcher) {
        // given
        val botUser = newUserEntity("searchable-bot").copy(
            name = "SearchBot",
            handle = "searchbot",
            userType = UserTypeInfoEntity.Bot
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
            assertEquals(UserTypeInfoEntity.Bot, result.first().userType)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenUserWithAppType_WhenSearchingByHandle_ThenUserTypeIsPreserved() = runTest(dispatcher) {
        // given
        val appUser = newUserEntity("searchable-app").copy(
            handle = "myapp",
            userType = UserTypeInfoEntity.App
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
            assertEquals(UserTypeInfoEntity.App, result.first().userType)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenUserWithRegularType_WhenUpdatingUser_ThenUserTypeIsPreserved() = runTest(dispatcher) {
        // given
        val user = newUserEntity("updatable-user").copy(
            userType = UserTypeInfoEntity.Regular(UserTypeEntity.EXTERNAL)
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
        assertEquals(UserTypeInfoEntity.Regular(UserTypeEntity.EXTERNAL), result?.userType)
        assertEquals("Updated Name", result?.name)
    }

    @Test
    fun givenUsersWithDifferentTypes_WhenGettingAllUsers_ThenAllTypesAreMappedCorrectly() = runTest(dispatcher) {
        // given
        val users = listOf(
            newUserEntity("user1").copy(userType = UserTypeInfoEntity.Regular(UserTypeEntity.STANDARD)),
            newUserEntity("user2").copy(userType = UserTypeInfoEntity.Regular(UserTypeEntity.ADMIN)),
            newUserEntity("user3").copy(userType = UserTypeInfoEntity.Bot),
            newUserEntity("user4").copy(userType = UserTypeInfoEntity.App),
            newUserEntity("user5").copy(userType = UserTypeInfoEntity.Regular(UserTypeEntity.GUEST))
        )

        // when
        db.userDAO.upsertUsers(users)
        val allUsers = db.userDAO.getAllUsersDetails().first()

        // then
        assertEquals(5, allUsers.size)
        val userTypesMap = allUsers.associate { it.id.value to it.userType }

        assertEquals(UserTypeInfoEntity.Regular(UserTypeEntity.STANDARD), userTypesMap["user1"])
        assertEquals(UserTypeInfoEntity.Regular(UserTypeEntity.ADMIN), userTypesMap["user2"])
        assertEquals(UserTypeInfoEntity.Bot, userTypesMap["user3"])
        assertEquals(UserTypeInfoEntity.App, userTypesMap["user4"])
        assertEquals(UserTypeInfoEntity.Regular(UserTypeEntity.GUEST), userTypesMap["user5"])
    }
}


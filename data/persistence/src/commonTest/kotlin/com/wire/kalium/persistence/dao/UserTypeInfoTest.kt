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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for UserTypeInfo enum and its mapping with UserTypeEntity.
 * Verifies that UserTypeEntity correctly returns the appropriate UserTypeInfo category.
 */
class UserTypeInfoTest {

    @Test
    fun givenUserTypeInfoEnum_whenCheckingAllValues_thenHasThreeValues() {
        // given / when
        val values = UserTypeInfo.entries

        // then
        assertEquals(3, values.size)
        assertEquals(UserTypeInfo.REGULAR, values[0])
        assertEquals(UserTypeInfo.APP, values[1])
        assertEquals(UserTypeInfo.BOT, values[2])
    }

    @Test
    fun givenOwnerUserType_whenGettingUserTypeInfo_thenReturnsRegular() {
        // given
        val userType = UserTypeEntity.OWNER

        // when
        val result = userType.getUserTypeInfo()

        // then
        assertEquals(UserTypeInfo.REGULAR, result)
    }

    @Test
    fun givenAdminUserType_whenGettingUserTypeInfo_thenReturnsRegular() {
        // given
        val userType = UserTypeEntity.ADMIN

        // when
        val result = userType.getUserTypeInfo()

        // then
        assertEquals(UserTypeInfo.REGULAR, result)
    }

    @Test
    fun givenStandardUserType_whenGettingUserTypeInfo_thenReturnsRegular() {
        // given
        val userType = UserTypeEntity.STANDARD

        // when
        val result = userType.getUserTypeInfo()

        // then
        assertEquals(UserTypeInfo.REGULAR, result)
    }

    @Test
    fun givenExternalUserType_whenGettingUserTypeInfo_thenReturnsRegular() {
        // given
        val userType = UserTypeEntity.EXTERNAL

        // when
        val result = userType.getUserTypeInfo()

        // then
        assertEquals(UserTypeInfo.REGULAR, result)
    }

    @Test
    fun givenFederatedUserType_whenGettingUserTypeInfo_thenReturnsRegular() {
        // given
        val userType = UserTypeEntity.FEDERATED

        // when
        val result = userType.getUserTypeInfo()

        // then
        assertEquals(UserTypeInfo.REGULAR, result)
    }

    @Test
    fun givenGuestUserType_whenGettingUserTypeInfo_thenReturnsRegular() {
        // given
        val userType = UserTypeEntity.GUEST

        // when
        val result = userType.getUserTypeInfo()

        // then
        assertEquals(UserTypeInfo.REGULAR, result)
    }

    @Test
    fun givenNoneUserType_whenGettingUserTypeInfo_thenReturnsRegular() {
        // given
        val userType = UserTypeEntity.NONE

        // when
        val result = userType.getUserTypeInfo()

        // then
        assertEquals(UserTypeInfo.REGULAR, result)
    }

    @Test
    fun givenServiceUserType_whenGettingUserTypeInfo_thenReturnsBot() {
        // given
        val userType = UserTypeEntity.SERVICE

        // when
        val result = userType.getUserTypeInfo()

        // then
        assertEquals(UserTypeInfo.BOT, result)
    }

    @Test
    fun givenAppUserType_whenGettingUserTypeInfo_thenReturnsApp() {
        // given
        val userType = UserTypeEntity.APP

        // when
        val result = userType.getUserTypeInfo()

        // then
        assertEquals(UserTypeInfo.APP, result)
    }

    @Test
    fun givenAllUserTypeEntities_whenMappingToUserTypeInfo_thenCorrectCategorization() {
        // given
        val allUserTypes = UserTypeEntity.entries

        // when / then
        val regularTypes = allUserTypes.filter { it.getUserTypeInfo() == UserTypeInfo.REGULAR }
        val botTypes = allUserTypes.filter { it.getUserTypeInfo() == UserTypeInfo.BOT }
        val appTypes = allUserTypes.filter { it.getUserTypeInfo() == UserTypeInfo.APP }

        // Verify REGULAR types
        assertEquals(7, regularTypes.size)
        assertEquals(
            setOf(
                UserTypeEntity.OWNER,
                UserTypeEntity.ADMIN,
                UserTypeEntity.STANDARD,
                UserTypeEntity.EXTERNAL,
                UserTypeEntity.FEDERATED,
                UserTypeEntity.GUEST,
                UserTypeEntity.NONE
            ),
            regularTypes.toSet()
        )

        // Verify BOT types
        assertEquals(1, botTypes.size)
        assertEquals(setOf(UserTypeEntity.SERVICE), botTypes.toSet())

        // Verify APP types
        assertEquals(1, appTypes.size)
        assertEquals(setOf(UserTypeEntity.APP), appTypes.toSet())
    }

    @Test
    fun givenRegularUserTypes_whenGroupedByUserTypeInfo_thenAllMapToSameCategory() {
        // given
        val regularTypes = listOf(
            UserTypeEntity.OWNER,
            UserTypeEntity.ADMIN,
            UserTypeEntity.STANDARD,
            UserTypeEntity.EXTERNAL,
            UserTypeEntity.FEDERATED,
            UserTypeEntity.GUEST,
            UserTypeEntity.NONE
        )

        // when
        val userTypeInfos = regularTypes.map { it.getUserTypeInfo() }.toSet()

        // then
        assertEquals(1, userTypeInfos.size)
        assertEquals(UserTypeInfo.REGULAR, userTypeInfos.first())
    }

    @Test
    fun givenServiceAndAppTypes_whenGroupedByUserTypeInfo_thenEachHasUniqueCategory() {
        // given
        val serviceType = UserTypeEntity.SERVICE
        val appType = UserTypeEntity.APP

        // when
        val serviceInfo = serviceType.getUserTypeInfo()
        val appInfo = appType.getUserTypeInfo()

        // then
        assertEquals(UserTypeInfo.BOT, serviceInfo)
        assertEquals(UserTypeInfo.APP, appInfo)
        // Verify they are different
        assertTrue(serviceInfo != appInfo)
    }

    @Test
    fun givenAllUserTypeEntities_whenCountingByCategory_thenCorrectDistribution() {
        // given
        val allUserTypes = UserTypeEntity.entries

        // when
        val categoryCount = allUserTypes.groupBy { it.getUserTypeInfo() }
            .mapValues { it.value.size }

        // then
        assertEquals(3, categoryCount.size) // 3 categories
        assertEquals(7, categoryCount[UserTypeInfo.REGULAR]) // 7 regular types
        assertEquals(1, categoryCount[UserTypeInfo.BOT]) // 1 bot type
        assertEquals(1, categoryCount[UserTypeInfo.APP]) // 1 app type
    }

    @Test
    fun givenUserTypeEntity_whenCheckingIsBot_thenOnlyServiceReturnsTrue() {
        // given / when / then
        UserTypeEntity.entries.forEach { userType ->
            val isBot = userType.getUserTypeInfo() == UserTypeInfo.BOT
            if (userType == UserTypeEntity.SERVICE) {
                assertEquals(true, isBot, "SERVICE should be categorized as BOT")
            } else {
                assertEquals(false, isBot, "$userType should not be categorized as BOT")
            }
        }
    }

    @Test
    fun givenUserTypeEntity_whenCheckingIsApp_thenOnlyAppReturnsTrue() {
        // given / when / then
        UserTypeEntity.entries.forEach { userType ->
            val isApp = userType.getUserTypeInfo() == UserTypeInfo.APP
            if (userType == UserTypeEntity.APP) {
                assertEquals(true, isApp, "APP should be categorized as APP")
            } else {
                assertEquals(false, isApp, "$userType should not be categorized as APP")
            }
        }
    }

    @Test
    fun givenUserTypeEntity_whenCheckingIsRegular_thenAllExceptServiceAndAppReturnTrue() {
        // given
        val expectedRegularTypes = setOf(
            UserTypeEntity.OWNER,
            UserTypeEntity.ADMIN,
            UserTypeEntity.STANDARD,
            UserTypeEntity.EXTERNAL,
            UserTypeEntity.FEDERATED,
            UserTypeEntity.GUEST,
            UserTypeEntity.NONE
        )

        // when / then
        UserTypeEntity.entries.forEach { userType ->
            val isRegular = userType.getUserTypeInfo() == UserTypeInfo.REGULAR
            if (userType in expectedRegularTypes) {
                assertEquals(true, isRegular, "$userType should be categorized as REGULAR")
            } else {
                assertEquals(false, isRegular, "$userType should not be categorized as REGULAR")
            }
        }
    }

    @Test
    fun givenUserEntity_whenStoredWithDifferentUserTypes_thenUserTypeInfoIsCorrectlyDerived() {
        // This test demonstrates the relationship between UserEntity.userType and UserTypeInfo
        // given
        val regularUser = UserTypeEntity.STANDARD
        val botUser = UserTypeEntity.SERVICE
        val appUser = UserTypeEntity.APP

        // when
        val regularInfo = regularUser.getUserTypeInfo()
        val botInfo = botUser.getUserTypeInfo()
        val appInfo = appUser.getUserTypeInfo()

        // then
        assertEquals(UserTypeInfo.REGULAR, regularInfo)
        assertEquals(UserTypeInfo.BOT, botInfo)
        assertEquals(UserTypeInfo.APP, appInfo)
    }

    @Test
    fun givenTeamMemberTypes_whenMappingToUserTypeInfo_thenAllAreRegular() {
        // given - all team member types
        val teamMemberTypes = listOf(
            UserTypeEntity.OWNER,
            UserTypeEntity.ADMIN,
            UserTypeEntity.STANDARD,
            UserTypeEntity.EXTERNAL
        )

        // when
        val userTypeInfos = teamMemberTypes.map { it.getUserTypeInfo() }

        // then
        userTypeInfos.forEach { userTypeInfo ->
            assertEquals(UserTypeInfo.REGULAR, userTypeInfo)
        }
    }

    @Test
    fun givenGuestAndFederatedTypes_whenMappingToUserTypeInfo_thenBothAreRegular() {
        // given
        val guestType = UserTypeEntity.GUEST
        val federatedType = UserTypeEntity.FEDERATED

        // when
        val guestInfo = guestType.getUserTypeInfo()
        val federatedInfo = federatedType.getUserTypeInfo()

        // then
        assertEquals(UserTypeInfo.REGULAR, guestInfo)
        assertEquals(UserTypeInfo.REGULAR, federatedInfo)
    }
}


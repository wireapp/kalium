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
package com.wire.kalium.logic.feature.channels

import app.cash.turbine.test
import com.wire.kalium.logic.configuration.ChannelsConfigurationStorage
import com.wire.kalium.logic.data.featureConfig.ChannelFeatureConfiguration
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.persistence.TestUserDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ObserveChannelsCreationPermissionUseCaseTest {

    private val dispatcher = StandardTestDispatcher()
    private val userDatabase = TestUserDatabase(TestUser.ENTITY_ID, dispatcher)
    private val configurationStorage = ChannelsConfigurationStorage(userDatabase.builder.metadataDAO)

    @AfterTest
    fun tearDown() {
        userDatabase.delete()
    }

    private fun runTest(testBody: suspend TestScope.() -> Unit) = runTest(dispatcher, testBody = testBody)

    private fun testSubject(selfUserFlow: Flow<SelfUser>) =
        ObserveChannelsCreationPermissionUseCase(configurationStorage, { selfUserFlow })

    @Test
    fun givenNoFeatureConfigurationStored_thenShouldReturnDisabledStatus() = runTest {
        val result = testSubject(flowOf(TestUser.SELF)).invoke()

        result.test {
            assertEquals(ChannelCreationPermission.Forbidden, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenDisabledFeature_thenShouldReturnDisabledStatus() = runTest {
        configurationStorage.persistChannelsConfiguration(ChannelFeatureConfiguration.Disabled)

        val result = testSubject(flowOf(TestUser.SELF)).invoke()

        result.test {
            assertEquals(ChannelCreationPermission.Forbidden, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun singleTest(
        userType: UserType,
        persistedConfiguration: ChannelFeatureConfiguration,
        assertion: (ChannelCreationPermission) -> Unit
    ) {
        configurationStorage.persistChannelsConfiguration(persistedConfiguration)

        val result = testSubject(flowOf(TestUser.SELF.copy(userType = userType))).invoke()

        result.test {
            val status = awaitItem()
            assertion(status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenCreationOfPublicChannelsRequiresAdminType_whenUserIsRegularMember_thenShouldReturnFalse() = runTest {
        singleTest(
            UserType.INTERNAL,
            ChannelFeatureConfiguration.Enabled(
                createChannelsRequirement = ChannelFeatureConfiguration.TeamUserType.EVERYONE_IN_THE_TEAM,
                createPublicChannelsRequirement = ChannelFeatureConfiguration.TeamUserType.ADMINS_ONLY,
            )
        ) { status ->
            assertIs<ChannelCreationPermission.Allowed>(status)
            assertFalse { status.canSelfUserCreatePublicChannels }
        }
    }

    @Test
    fun givenCreationOfPublicChannelsRequiresAdminType_whenUserIsAdminMember_thenShouldReturnTrue() = runTest {
        singleTest(
            UserType.ADMIN,
            ChannelFeatureConfiguration.Enabled(
                createChannelsRequirement = ChannelFeatureConfiguration.TeamUserType.EVERYONE_IN_THE_TEAM,
                createPublicChannelsRequirement = ChannelFeatureConfiguration.TeamUserType.ADMINS_ONLY,
            )
        ) { status ->
            assertIs<ChannelCreationPermission.Allowed>(status)
            assertTrue { status.canSelfUserCreatePublicChannels }
        }
    }

    @Test
    fun givenCreationOfRegularChannelsRequiresRegularMember_whenUserIsExternal_thenShouldReturnFalse() = runTest {
        singleTest(
            UserType.EXTERNAL, ChannelFeatureConfiguration.Enabled(
                createChannelsRequirement = ChannelFeatureConfiguration.TeamUserType.ADMINS_AND_REGULAR_MEMBERS,
                createPublicChannelsRequirement = ChannelFeatureConfiguration.TeamUserType.ADMINS_ONLY,
            )
        ) { status ->
            assertIs<ChannelCreationPermission.Forbidden>(status)
        }
    }

    @Test
    fun givenConfigsUpdate_thenShouldEmitNewValues() = runTest {
        configurationStorage.persistChannelsConfiguration(ChannelFeatureConfiguration.Disabled)

        val result = testSubject(flowOf(TestUser.SELF.copy(userType = UserType.ADMIN))).invoke()

        result.test {
            val status = awaitItem()
            assertIs<ChannelCreationPermission.Forbidden>(status)
            configurationStorage.persistChannelsConfiguration(
                ChannelFeatureConfiguration.Enabled(
                    ChannelFeatureConfiguration.TeamUserType.ADMINS_AND_REGULAR_MEMBERS,
                    ChannelFeatureConfiguration.TeamUserType.ADMINS_AND_REGULAR_MEMBERS,
                )
            )
            advanceUntilIdle()
            val newStatus = awaitItem()
            assertIs<ChannelCreationPermission.Allowed>(newStatus)
            assertTrue { newStatus.canSelfUserCreatePublicChannels }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenUserTypeUpdates_thenShouldEmitNewValues() = runTest {
        configurationStorage.persistChannelsConfiguration(
            ChannelFeatureConfiguration.Enabled(
                ChannelFeatureConfiguration.TeamUserType.ADMINS_ONLY,
                ChannelFeatureConfiguration.TeamUserType.ADMINS_ONLY,
            )
        )

        val selfUserFlow = MutableStateFlow(TestUser.SELF.copy(userType = UserType.INTERNAL))
        val result = testSubject(selfUserFlow).invoke()

        result.test {
            val status = awaitItem()
            assertIs<ChannelCreationPermission.Forbidden>(status)
            selfUserFlow.value = TestUser.SELF.copy(userType = UserType.ADMIN)
            advanceUntilIdle()
            val newStatus = awaitItem()
            assertIs<ChannelCreationPermission.Allowed>(newStatus)
            assertTrue { newStatus.canSelfUserCreatePublicChannels }
            cancelAndIgnoreRemainingEvents()
        }
    }
}

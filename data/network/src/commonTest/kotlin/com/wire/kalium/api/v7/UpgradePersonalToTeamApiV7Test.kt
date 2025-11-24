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

package com.wire.kalium.api.v7

import com.wire.kalium.api.ApiTest
import com.wire.kalium.mocks.responses.MigrationUserToTeamResponseJson
import com.wire.kalium.network.api.v7.authenticated.UpgradePersonalToTeamApiV7
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue


internal class UpgradePersonalToTeamApiV7Test : ApiTest() {

    @Test
    fun whenCallingUpgradePersonalToTeam_thenTheRequestShouldBeConfiguredOK() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            SUCCESS_RESPONSE,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertPathEqual("upgrade-personal-to-team")
            }
        )
        val upgradePersonalToTeamApi = UpgradePersonalToTeamApiV7(networkClient)
        upgradePersonalToTeamApi.migrateToTeam(SUCCESS_RESPONSE)
    }

    @Test
    fun given200Response_whenCallingUpgradePersonalToTeam_thenResponseIsParsedCorrectly() =
        runTest {
            val networkClient = mockAuthenticatedNetworkClient(
                SUCCESS_RESPONSE,
                statusCode = HttpStatusCode.OK
            )
            val upgradePersonalToTeamApi = UpgradePersonalToTeamApiV7(networkClient)

            val upgradePersonalToTeam = upgradePersonalToTeamApi.migrateToTeam(TEAM_NAME)
            assertTrue(upgradePersonalToTeam.isSuccessful())
        }

    @Test
    fun givenUserInTeamResponse_whenCallingUpgradePersonalToTeam_thenResponseIsParsedCorrectly() =
        runTest {
            val networkClient = mockAuthenticatedNetworkClient(
                FAILED_USER_IN_TEAM_RESPONSE,
                statusCode = HttpStatusCode.Forbidden
            )
            val upgradePersonalToTeamApi = UpgradePersonalToTeamApiV7(networkClient)

            val upgradePersonalToTeam = upgradePersonalToTeamApi.migrateToTeam(TEAM_NAME)
            assertFalse(upgradePersonalToTeam.isSuccessful())
        }

    @Test
    fun givenUserNotFoundResponse_whenCallingUpgradePersonalToTeam_thenResponseIsParsedCorrectly() =
        runTest {
            val networkClient = mockAuthenticatedNetworkClient(
                FAILED_USER_NOT_FOUND_RESPONSE,
                statusCode = HttpStatusCode.NotFound
            )
            val upgradePersonalToTeamApi = UpgradePersonalToTeamApiV7(networkClient)

            val upgradePersonalToTeam = upgradePersonalToTeamApi.migrateToTeam(TEAM_NAME)
            assertFalse(upgradePersonalToTeam.isSuccessful())
        }

    companion object {
        val SUCCESS_RESPONSE =
            MigrationUserToTeamResponseJson.success.rawJson
        val TEAM_NAME =
            MigrationUserToTeamResponseJson.success.serializableData.teamName
        val FAILED_USER_IN_TEAM_RESPONSE =
            MigrationUserToTeamResponseJson.failedUserInTeam.rawJson
        val FAILED_USER_NOT_FOUND_RESPONSE =
            MigrationUserToTeamResponseJson.failedUserNotFound.rawJson
    }
}
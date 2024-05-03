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

package com.wire.kalium.api.v5

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.json.model.KeyPackageJson
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackageApi
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.api.v5.authenticated.KeyPackageApiV5
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class KeyPackageApiV5Test : ApiTest() {

    @Test
    fun givenAValidClientId_whenCallingGetAvailableKeyPackageCountEndpoint_theRequestShouldBeConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            KeyPackageJson.keyPackageCountJson(KEY_PACKAGE_COUNT).rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertPathEqual(KEY_PACKAGE_COUNT_PATH)
            }
        )
        val keyPackageApi: KeyPackageApi = KeyPackageApiV5(networkClient)

        val response = keyPackageApi.getAvailableKeyPackageCount(VALID_CLIENT_ID)
        assertTrue(response.isSuccessful())
        assertEquals(response.value.count, KEY_PACKAGE_COUNT)
    }

    @Test
    fun givenAValidClientId_whenCallingUploadKeyPackagesEndpoint_theRequestShouldBeConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            "",
            statusCode = HttpStatusCode.Created,
            assertion = {
                assertPost()
                assertJson()
                assertPathEqual(KEY_PACKAGE_UPLOAD_PATH)
            }
        )
        val keyPackageApi: KeyPackageApi = KeyPackageApiV5(networkClient)

        val response = keyPackageApi.uploadKeyPackages(VALID_CLIENT_ID, listOf(VALID_KEY_PACKAGE))
        assertTrue(response.isSuccessful())
    }

    @Test
    fun givenAValidClientId_whenCallingClaimKeyPackagesEndpoint_theRequestShouldBeConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            KeyPackageJson.valid.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertPathEqual(KEY_PACKAGE_CLAIM_PATH)
            }
        )
        val keyPackageApi: KeyPackageApi = KeyPackageApiV5(networkClient)

        val response = keyPackageApi.claimKeyPackages(KeyPackageApi.Param.IncludeOwnClient(VALID_USER_ID))
        assertTrue(response.isSuccessful())
        assertEquals(response.value, VALID_CLAIM_KEY_PACKAGES_RESPONSE.serializableData)
    }

    private companion object {
        const val KEY_PACKAGE_COUNT = 5
        val VALID_USER_ID = UserId("fdf23116-42a5-472c-8316-e10655f5d11e", "wire.com")
        const val VALID_CLIENT_ID = "defkrr8e7grgsoufhg8"
        const val VALID_KEY_PACKAGE = "BKqNPFDI7R0Ic6ACTtrGWOpfWw4="
        val VALID_CLAIM_KEY_PACKAGES_RESPONSE = KeyPackageJson.valid
        const val KEY_PACKAGE_COUNT_PATH = "/mls/key-packages/self/$VALID_CLIENT_ID/count"
        const val KEY_PACKAGE_UPLOAD_PATH = "/mls/key-packages/self/$VALID_CLIENT_ID"
        val KEY_PACKAGE_CLAIM_PATH = "/mls/key-packages/claim/${VALID_USER_ID.domain}/${VALID_USER_ID.value}"
    }
}

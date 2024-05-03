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
import com.wire.kalium.model.MLSPublicKeysResponseJson
import com.wire.kalium.network.api.base.authenticated.serverpublickey.MLSPublicKeyApi
import com.wire.kalium.network.api.v5.authenticated.MLSPublicKeyApiV5
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class MLSPublicKeysApiV5Test : ApiTest() {

    @Test
    fun givenWhenGetMLSPublicKeys_theRequestShouldBeConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            MLSPublicKeysResponseJson.valid.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion =
            {
                assertGet()
                assertContentType(ContentType.Application.Json)
                assertPathEqual("$PATH_MLS/$PATH_PUBLIC_KEYS")
            }
        )
        val mlsPublicKeyApi: MLSPublicKeyApi = MLSPublicKeyApiV5(networkClient)
        val response = mlsPublicKeyApi.getMLSPublicKeys()
        assertTrue(response.isSuccessful())
    }

    private companion object {
        const val PATH_PUBLIC_KEYS = "public-keys"
        const val PATH_MLS = "mls"
    }
}

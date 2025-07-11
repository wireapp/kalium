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
package com.wire.kalium.api.v9

import com.wire.kalium.api.ApiTest
import com.wire.kalium.mocks.responses.ServerTimeResponseJson
import com.wire.kalium.network.api.base.authenticated.ServerTimeApi
import com.wire.kalium.network.api.v9.authenticated.ServerTimeApiV9
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
internal class ServerTimeApiV9Test : ApiTest() {
    @Test
    fun givenAServerTimeIsRequest_whenCallingTheEndpoint_theRequestShouldBeConfiguredCorrectlyAndResponse() =
        runTest {
            val networkClient = mockAuthenticatedNetworkClient(
                VALID_SERVER_TIME_RESPONSE.rawJson,
                statusCode = HttpStatusCode.OK,
                assertion = {
                    assertGet()
                    assertNoQueryParams()
                    assertPathEqual(PATH_TIME)
                }
            )
            val serverTimeApi: ServerTimeApi = ServerTimeApiV9(networkClient)
            val response = serverTimeApi.getServerTime()
            assertTrue(response.isSuccessful())
            assertEquals(VALID_SERVER_TIME_RESPONSE.serializableData, response.value)
        }

    private companion object {
        const val PATH_TIME = "/time"
        val VALID_SERVER_TIME_RESPONSE = ServerTimeResponseJson.success
    }
}

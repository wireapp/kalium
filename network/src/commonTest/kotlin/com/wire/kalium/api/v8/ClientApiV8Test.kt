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

package com.wire.kalium.api.v8

import com.wire.kalium.api.ApiTest
import com.wire.kalium.mocks.responses.ClientResponseJson
import com.wire.kalium.mocks.responses.RegisterClientRequestJson
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.api.v8.authenticated.ClientApiV8
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
internal class ClientApiV8Test : ApiTest() {
    @Test
    fun givenAValidRegisterClientRequest_whenCallingTheRegisterClientEndpoint_theRequestShouldBeConfiguredCorrectly() =
        runTest {
            val networkClient = mockAuthenticatedNetworkClient(
                VALID_REGISTER_CLIENT_RESPONSE.rawJson,
                statusCode = HttpStatusCode.Created,
                assertion = {
                    assertPost()
                    assertJson()
                    assertNoQueryParams()
                    assertPathEqual(PATH_CLIENTS)
                }
            )
            val clientApi: ClientApi = ClientApiV8(networkClient)
            val response = clientApi.registerClient(REGISTER_CLIENT_REQUEST.serializableData)
            assertTrue(response.isSuccessful())
            assertEquals(VALID_REGISTER_CLIENT_RESPONSE.serializableData, response.value)
        }

    @Test
    fun givenAValidRegisterClientRequest_whenCallingTheRegisterClientEndpoint_theRequestShouldBeConfiguredCorrectlyWithConsumableCapability() =
        runTest {
            val clientCapability = "consumable-notifications"
            val networkClient = mockAuthenticatedNetworkClient(
                VALID_REGISTER_CLIENT_RESPONSE.rawJson,
                statusCode = HttpStatusCode.Created,
                assertion = {
                    assertPost()
                    assertJson()
                    assertNoQueryParams()
                    assertPathEqual(PATH_CLIENTS)
                    assertJsonBodyContains(clientCapability)
                }
            )
            val clientApi: ClientApi = ClientApiV8(networkClient)
            val response = clientApi.registerClient(REGISTER_CLIENT_REQUEST.serializableData)
            assertTrue(response.isSuccessful())
            assertEquals(VALID_REGISTER_CLIENT_RESPONSE.serializableData, response.value)
        }


    private companion object {
        const val PATH_CLIENTS = "/clients"
        val REGISTER_CLIENT_REQUEST = RegisterClientRequestJson.valid
        val VALID_REGISTER_CLIENT_RESPONSE = ClientResponseJson.valid
    }
}

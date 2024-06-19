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

package com.wire.kalium.api.v4

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.json.model.ErrorResponseJson
import com.wire.kalium.mocks.responses.connection.ConnectionResponsesJson
import com.wire.kalium.network.api.base.model.ErrorResponse
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.api.v4.authenticated.ConnectionApiV4
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class ConnectionApiV4Test : ApiTest() {

    @Test
    fun givenACreationRequest_whenRequestingAConnectionWithAnUser_thenShouldReturnsACorrectConnectionResponse() =
        runTest {
            // given
            val userId = UserId("user_id", "domain_id")
            val httpClient = mockAuthenticatedNetworkClient(
                CREATE_CONNECTION_RESPONSE.rawJson,
                statusCode = HttpStatusCode.OK,
                assertion = {
                    assertJson()
                    assertPost()
                    assertPathEqual("$PATH_CONNECTIONS_ENDPOINT/${userId.domain}/${userId.value}")
                }
            )
            // when
            val connectionApi = ConnectionApiV4(httpClient)
            val response = connectionApi.createConnection(userId)

            // then
            assertTrue(response.isSuccessful())

        }

    @Test
    fun givenACreationRequest_whenRequestingAConnectionWithAnNonFederatedUser_thenShouldReturnsAnErrorResponse() =
        runTest {
            // given
            val nonFederatingDomain = "bella.com"
            val errorResponse = ErrorResponseJson.valid(
                ErrorResponse(
                    code = HttpStatusCode.UnprocessableEntity.value,
                    message = "Backend does not federate with the backend of $nonFederatingDomain",
                    label = "federation-denied"
                )
            )
            val userId = UserId("user_id", nonFederatingDomain)
            val httpClient = mockAuthenticatedNetworkClient(
                errorResponse.rawJson,
                statusCode = HttpStatusCode.UnprocessableEntity,
                assertion = {
                    assertJson()
                    assertPost()
                    assertPathEqual("$PATH_CONNECTIONS_ENDPOINT/${userId.domain}/${userId.value}")
                }
            )
            // when
            val connectionApi = ConnectionApiV4(httpClient)
            val response = connectionApi.createConnection(userId)

            // then
            assertFalse(response.isSuccessful())
            assertTrue(response.kException is KaliumException.FederationError)
            assertTrue(
                (response.kException as KaliumException.FederationError).errorResponse.label == "federation-denied"
            )
        }

    private companion object {
        const val PATH_CONNECTIONS_ENDPOINT = "/connections"
        val CREATE_CONNECTION_RESPONSE = ConnectionResponsesJson.CreateConnectionResponse.jsonProvider
    }
}

/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.api.v0.prekey

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.json.model.DomainToUserIdToClientToPreKeyMapJson
import com.wire.kalium.api.json.model.DomainToUserIdToClientsMapJson
import com.wire.kalium.api.json.model.ErrorResponseJson
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyApi
import com.wire.kalium.network.api.v0.authenticated.PreKeyApiV0
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class PrekeyApiV0Test : ApiTest {

    @Test
    fun givenAValidDomainToUserIdToClientsMap_whenCallingGetUsersPrekeyEndpoint_theRequestShouldBeConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            VALID_GET_USERS_PREKEY_RESPONSE.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertJson()
                assertPathEqual(PATH_PREKEYS)
            }
        )
        val preKeyApi: PreKeyApi = PreKeyApiV0(networkClient)

        val response = preKeyApi.getUsersPreKey(VALID_GET_USERS_PREKEY_REQUEST.serializableData)
        assertTrue(response.isSuccessful())
        assertEquals(response.value, VALID_GET_USERS_PREKEY_RESPONSE.serializableData)
    }

    @Test
    fun givenTheServerReturnsAnError_whenCallingGetUsersPrekeyEndpoint_theCorrectExceptionIsThrown() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            ErrorResponseJson.valid.rawJson,
            statusCode = HttpStatusCode.Forbidden,
        )
        val preKeyApi: PreKeyApi = PreKeyApiV0(networkClient)
        val errorResponse = preKeyApi.getUsersPreKey(VALID_GET_USERS_PREKEY_REQUEST.serializableData)
        assertFalse(errorResponse.isSuccessful())
        assertTrue(errorResponse.kException is KaliumException.InvalidRequestError)
        assertEquals((errorResponse.kException as KaliumException.InvalidRequestError).errorResponse, ERROR_RESPONSE)
    }

    private companion object {
        val VALID_GET_USERS_PREKEY_REQUEST = DomainToUserIdToClientsMapJson.valid
        val VALID_GET_USERS_PREKEY_RESPONSE = DomainToUserIdToClientToPreKeyMapJson.valid
        val ERROR_RESPONSE = ErrorResponseJson.valid.serializableData
        const val PATH_PREKEYS = "/users/list-prekeys"
    }
}

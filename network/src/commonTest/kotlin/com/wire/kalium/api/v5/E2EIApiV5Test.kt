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
import com.wire.kalium.network.api.base.authenticated.e2ei.E2EIApi
import com.wire.kalium.network.api.v5.authenticated.E2EIApiV5
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class E2EIApiV5Test : ApiTest() {

    @Test
    fun giveAValidResponseWithNonceInHeader_whenCallingNonceApi_theResponseShouldBeConfigureCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            "",
            statusCode = HttpStatusCode.Created,
            headers = mapOf(NONCE_HEADER_KEY to RANDOM_NONCE),
            assertion = {
                assertHead()
                assertJsonJose()
                assertPathEqual(NONCE_PATH)
            }
        )
        val e2EIApi: E2EIApi = E2EIApiV5(networkClient)

        val response = e2EIApi.getWireNonce(VALID_CLIENT_ID)
        assertTrue(response.isSuccessful())
        assertEquals(response.value, RANDOM_NONCE)
    }

    @Test
    fun giveAResponseMissingNonceHeader_whenCallingNonceApi_theResponseShouldMissingNonceError() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            "",
            statusCode = HttpStatusCode.Created,
            assertion = {
                assertHead()
                assertJsonJose()
                assertPathEqual(NONCE_PATH)
            }
        )
        val e2EIApi: E2EIApi = E2EIApiV5(networkClient)

        val response = e2EIApi.getWireNonce(VALID_CLIENT_ID)
        assertFalse(response.isSuccessful())
    }

    private companion object {
        const val VALID_CLIENT_ID = "defkrr8e7grgsoufhg8"
        const val PATH_CLIENTS = "clients"
        const val PATH_NONCE = "nonce"
        const val RANDOM_NONCE = "random-nonce"
        const val NONCE_HEADER_KEY = "Replay-Nonce"
        const val NONCE_PATH = "$PATH_CLIENTS/$VALID_CLIENT_ID/$PATH_NONCE"
    }
}

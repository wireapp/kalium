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
package com.wire.kalium.api.common

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.json.model.ACMEApiResponseJsonSample
import com.wire.kalium.api.v4.E2EIApiV4Test
import com.wire.kalium.network.api.base.authenticated.e2ei.E2EIApi
import com.wire.kalium.network.api.base.unbound.acme.ACMEApi
import com.wire.kalium.network.api.base.unbound.acme.ACMEApiImpl
import com.wire.kalium.network.api.base.unbound.acme.AcmeDirectoriesResponse
import com.wire.kalium.network.api.v4.authenticated.E2EIApiV4
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class ACMEApiTest  : ApiTest(){

    //getACMEDirectories
    @Test
    fun whenCallingGetACMEDirectoriesApi_theResponseShouldBeConfigureCorrectly() = runTest {
        val expected = ACME_DIRECTORIES_SAMPLE
        val networkClient = mockUnboundNetworkClient(
            ACME_DIRECTORIES_RESPONSE,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertJson()
                assertPathEqual(DIRECTORY_API_PATH)
                assertGet()
                assertNoQueryParams()
            }
        )
        val acmeApi: ACMEApi = ACMEApiImpl(networkClient)

        acmeApi.getACMEDirectories().also { actual ->
            assertIs<NetworkResponse.Success<AcmeDirectoriesResponse>>(actual)
            assertEquals(expected, actual.value)
        }
    }

    @Test
    fun whenCallingGetACMENonceApi_theResponseShouldBeConfigureCorrectly() = runTest {
        val networkClient = mockUnboundNetworkClient(
            "",
            statusCode = HttpStatusCode.OK,
            headers = mapOf(NONCE_HEADER_KEY to RANDOM_NONCE),
            assertion = {
                assertJson()
                assertPathEqual(ACME_DIRECTORIES_SAMPLE.newNonce)
                assertGet()
                assertNoQueryParams()
            }
        )
        val acmeApi: ACMEApi = ACMEApiImpl(networkClient)

        acmeApi.getACMENonce(ACME_DIRECTORIES_SAMPLE.newNonce).also { actual ->
            assertIs<NetworkResponse.Success<String>>(actual)
            assertEquals(RANDOM_NONCE, actual.value)
        }
    }

    //getACMENonce
    //sendACMERequest
    //sendChallengeRequest

    companion object{
        private const val BASE_URL = "https://balderdash.hogwash.work"
        private const val ACME_PORT = "9000"
        private const val PATH_ACME_DIRECTORIES = "acme/google-android/directory"
        const val DIRECTORY_API_PATH = "/$PATH_ACME_DIRECTORIES"

        val ACME_DIRECTORIES_RESPONSE = ACMEApiResponseJsonSample.valid.rawJson
        val ACME_DIRECTORIES_SAMPLE = ACMEApiResponseJsonSample.ACME_DIRECTORIES_SAMPLE
        const val RANDOM_NONCE = "random-nonce"
        const val NONCE_HEADER_KEY = "Replay-Nonce"
    }
}

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
package com.wire.kalium.api.common

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.json.model.ACMEApiResponseJsonSample
import com.wire.kalium.api.json.model.ACMEApiResponseJsonSample.ACME_RESPONSE_SAMPLE
import com.wire.kalium.api.json.model.ACMEApiResponseJsonSample.jsonProviderAcmeChallengeResponse
import com.wire.kalium.network.api.base.unbound.acme.*
import com.wire.kalium.network.api.unbound.acme.ACMEResponse
import com.wire.kalium.network.api.unbound.acme.AcmeDirectoriesResponse
import com.wire.kalium.network.api.unbound.acme.CertificateChain
import com.wire.kalium.network.api.unbound.acme.ChallengeResponse
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

internal class ACMEApiTest : ApiTest() {

    @Test
    fun givingASuccessfulResponse_whenGettingACMEFederationCertificateChain_thenAllCertificatesShouldBeParsed() = runTest {
        val expected = listOf("a", "b", "potato")

        val networkClient = mockUnboundNetworkClient(
            responseBody = """
                 {
                     "crts": ["a", "b", "potato"]
                 }
            """.trimIndent(),
            statusCode = HttpStatusCode.OK
        )

        val acmeApi: ACMEApi = ACMEApiImpl(networkClient, networkClient)

        val result = acmeApi.getACMEFederationCertificateChain("someURL")

        assertTrue(result.isSuccessful())
        assertContentEquals(expected, result.value)
    }

    @Ignore
    @Test
    fun whenCallingGeTrustAnchorsApi_theResponseShouldBeConfigureCorrectly() = runTest {
        val expected = CertificateChain("")
        val networkClient = mockUnboundNetworkClient(
            "",
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertJson()
                assertUrlEqual(ACME_ROOTS_PEM_PATH)
                assertGet()
                assertNoQueryParams()
            }
        )
        val acmeApi: ACMEApi = ACMEApiImpl(networkClient, networkClient)

        acmeApi.getTrustAnchors(ACME_DISCOVERY_URL).also { actual ->
            assertIs<NetworkResponse.Success<CertificateChain>>(actual)
            assertEquals(expected, actual.value)
        }
    }

    @Test
    fun whenCallingGetACMEDirectoriesApi_theResponseShouldBeConfigureCorrectly() = runTest {
        val expected = ACME_DIRECTORIES_SAMPLE
        val networkClient = mockUnboundNetworkClient(
            ACME_DIRECTORIES_RESPONSE,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertJson()
                assertUrlEqual(ACME_DIRECTORIES_PATH)
                assertGet()
                assertNoQueryParams()
            }
        )
        val acmeApi: ACMEApi = ACMEApiImpl(networkClient, networkClient)

        acmeApi.getACMEDirectories(ACME_DISCOVERY_URL).also { actual ->
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
                assertUrlEqual(ACME_DIRECTORIES_SAMPLE.newNonce)
                assertHead()
                assertNoQueryParams()
            }
        )
        val acmeApi: ACMEApi = ACMEApiImpl(networkClient, networkClient)

        acmeApi.getACMENonce(ACME_DIRECTORIES_SAMPLE.newNonce).also { actual ->
            assertIs<NetworkResponse.Success<String>>(actual)
            assertEquals(RANDOM_NONCE, actual.value)
        }
    }

    @Test
    fun whenCallingSendAcmeRequestApi_theResponseShouldBeConfigureCorrectly() = runTest {
        val randomResponse = ACME_RESPONSE_SAMPLE.rawJson
        val networkClient = mockUnboundNetworkClient(
            randomResponse,
            statusCode = HttpStatusCode.OK,
            headers = mapOf(NONCE_HEADER_KEY to RANDOM_NONCE, LOCATION_HEADER_KEY to RANDOM_LOCATION),
            assertion = {
                assertJsonJose()
                assertUrlEqual("http://wire.com")
                assertPost()
                assertNoQueryParams()
            }
        )
        val acmeApi: ACMEApi = ACMEApiImpl(networkClient, networkClient)

        acmeApi.sendACMERequest("http://wire.com", byteArrayOf(0x12, 0x24, 0x32, 0x42)).also { actual ->
            assertIs<NetworkResponse.Success<ACMEResponse>>(actual)
            assertEquals(RANDOM_NONCE, actual.value.nonce)
            assertEquals(RANDOM_LOCATION, actual.value.location)
            assertEquals(randomResponse, actual.value.response.decodeToString())
        }
    }

    @Test
    fun givenNoNonce_whenCallingSendAcmeRequestApi_theResponseShouldBeMissingNonce() = runTest {
        val randomResponse = ACME_RESPONSE_SAMPLE.rawJson
        val networkClient = mockUnboundNetworkClient(
            randomResponse,
            statusCode = HttpStatusCode.OK,
            headers = mapOf(LOCATION_HEADER_KEY to RANDOM_LOCATION),
            assertion = {
                assertJsonJose()
                assertUrlEqual(ACME_DIRECTORIES_SAMPLE.newNonce)
                assertPost()
                assertNoQueryParams()
            }
        )
        val acmeApi: ACMEApi = ACMEApiImpl(networkClient, networkClient)

        val response = acmeApi.sendACMERequest(ACME_DIRECTORIES_SAMPLE.newNonce)
        assertFalse(response.isSuccessful())
    }

    @Test
    fun givenNoLocationInHeader_whenCallingSendAcmeRequestApi_theResponseShouldBeConfigureCorrectly() = runTest {
        val randomResponse = ACME_RESPONSE_SAMPLE.rawJson
        val networkClient = mockUnboundNetworkClient(
            randomResponse,
            statusCode = HttpStatusCode.OK,
            headers = mapOf(NONCE_HEADER_KEY to RANDOM_NONCE),
            assertion = {
                assertJsonJose()
                assertUrlEqual("http://wire.com")
                assertPost()
                assertNoQueryParams()
            }
        )
        val acmeApi: ACMEApi = ACMEApiImpl(networkClient, networkClient)

        acmeApi.sendACMERequest("http://wire.com", byteArrayOf(0x12, 0x24, 0x32, 0x42)).also { actual ->
            assertIs<NetworkResponse.Success<ACMEResponse>>(actual)
            assertEquals(RANDOM_NONCE, actual.value.nonce)
            assertEquals("null", actual.value.location)
            assertEquals(randomResponse, actual.value.response.decodeToString())
        }
    }

    @Ignore
    @Test
    fun whenCallingSendChallengeRequestApi_theResponseShouldBeConfigureCorrectly() = runTest {
        val expected = ACMEApiResponseJsonSample.ACME_CHALLENGE_RESPONSE_SAMPLE
        val response = jsonProviderAcmeChallengeResponse.rawJson
        val networkClient = mockUnboundNetworkClient(
            response,
            statusCode = HttpStatusCode.OK,
            headers = mapOf(NONCE_HEADER_KEY to expected.nonce),
            assertion = {
                assertJsonJose()
                assertUrlEqual(RANDOM_CHALLENGE_URL)
                assertPost()
                assertNoQueryParams()
            }
        )
        val acmeApi: ACMEApi = ACMEApiImpl(networkClient, networkClient)

        acmeApi.sendChallengeRequest(RANDOM_CHALLENGE_URL, byteArrayOf(0x12, 0x24, 0x32, 0x42)).also { actual ->
            assertIs<NetworkResponse.Success<ChallengeResponse>>(actual)
            assertEquals(expected, actual.value)
        }
    }

    companion object {
        private const val ACME_DISCOVERY_URL = "https://balderdash.hogwash.work:9000/acme/google-android/directory"
        private const val ACME_DIRECTORIES_PATH = "https://balderdash.hogwash.work:9000/acme/google-android/directory"
        private const val ACME_ROOTS_PEM_PATH = "https://balderdash.hogwash.work:9000"

        val ACME_DIRECTORIES_RESPONSE = ACMEApiResponseJsonSample.validAcmeDirectoriesResponse.rawJson
        val ACME_DIRECTORIES_SAMPLE = ACMEApiResponseJsonSample.ACME_DIRECTORIES_SAMPLE
        const val RANDOM_NONCE = "random-nonce"
        const val NONCE_HEADER_KEY = "Replay-Nonce"
        const val LOCATION_HEADER_KEY = "location"
        const val RANDOM_LOCATION = "https://balderdash.hogwash.work/random-location"
        const val RANDOM_CHALLENGE_URL = "https://balderdash.hogwash.work/random-challenge"

    }
}

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

import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.network.api.base.unbound.acme.AcmeDirectoriesResponse
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import util.MockUnboundNetworkClient
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TempTest {
    @Test
    fun testFun() = runTest {
        val networkClient = MockUnboundNetworkClient.mockUnboundNetworkClient(
            ACME_DIRECTORIES_RESPONSE,
            statusCode = HttpStatusCode.OK,
            assertion = {
                val contentType = ContentType.Application.Json.withParameter("charset", "UTF-8")
                assertTrue(
                    contentType.match(this.body.contentType ?: ContentType.Any),
                    "contentType: ${this.body.contentType} doesn't match expected contentType: $contentType"
                )
                assertContains(this.url.toString(), ACME_DIRECTORIES_PATH)
                assertEquals(this.method, method)
                assertTrue(this.url.parameters.names().isEmpty())
            }
        )

        val coreLogic = coreLogic(
            rootPath = "$HOME_DIRECTORY/.kalium/accounts",
            kaliumConfigs = KaliumConfigs(
                developmentApiEnabled = true,
                encryptProteusStorage = true,
                isMLSSupportEnabled = true,
                wipeOnDeviceRemoval = true,
            ),
            networkClient = networkClient
        )

        launch {
//             val expectedResult = AcmeDirectoriesResponse(
//                 newNonce = "nonce",
//                 newAccount = "newAccount",
//                 newOrder = "newOrder",
//                 revokeCert = "revokeCert",
//                 keyChange = "keyChange"
//             )
//
//             val result = coreLogic
//                 .getGlobalScope()
//                 .unboundNetworkContainer
//                 .value.acmeApi
//                 .getACMEDirectories()
//
//             assertTrue(result.isSuccessful())
//             assertEquals(expectedResult.newNonce, result.value.newNonce)
//             assertEquals(expectedResult.newAccount, result.value.newAccount)
//             assertEquals(expectedResult.newOrder, result.value.newOrder)
//             assertEquals(expectedResult.revokeCert, result.value.revokeCert)
//             assertEquals(expectedResult.keyChange, result.value.keyChange)

            val expected = ACME_DIRECTORIES_SAMPLE

            coreLogic.getGlobalScope().unboundNetworkContainer
                .value.acmeApi.getACMEDirectories().also { actual ->
                    assertIs<NetworkResponse.Success<AcmeDirectoriesResponse>>(actual)
                    assertEquals(expected, actual.value)
                }

            /*

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
            val acmeApi: ACMEApi = ACMEApiImpl(networkClient)

            acmeApi.getACMEDirectories().also { actual ->
                assertIs<NetworkResponse.Success<AcmeDirectoriesResponse>>(actual)
                assertEquals(expected, actual.value)
            }
             */
        }
    }

    companion object {
        val HOME_DIRECTORY: String = homeDirectory()

        private const val ACME_DIRECTORIES_PATH = "https://balderdash.hogwash.work:9000/acme/google-android/directory"

        val ACME_DIRECTORIES_RESPONSE = ACMEApiResponseJsonSample.validAcmeDirectoriesResponse.rawJson
        val ACME_DIRECTORIES_SAMPLE = ACMEApiResponseJsonSample.ACME_DIRECTORIES_SAMPLE
    }
}

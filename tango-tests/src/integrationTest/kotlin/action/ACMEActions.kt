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
package action

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.network.api.base.unbound.acme.AcmeDirectoriesResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import util.MockUnboundNetworkClient
import kotlin.test.assertEquals
import kotlin.test.assertIs

object ACMEActions {

    suspend fun acmeDirectoriesErrorNotConnected(coreLogic: CoreLogic) = coreLogic.getGlobalScope()
        .unboundNetworkContainer
        .acmeApi.getACMEDirectories(ACME_BASE_URL).also { actual ->
            assertIs<NetworkResponse.Error>(actual)
            assertIs<KaliumException.NoNetwork>(actual.kException.cause)
        }

    suspend fun acmeDirectoriesSuccess(coreLogic: CoreLogic, expected: AcmeDirectoriesResponse = ACME_DIRECTORIES_SAMPLE) =
        coreLogic.getGlobalScope()
            .unboundNetworkContainer
            .acmeApi.getACMEDirectories(ACME_BASE_URL).also { actual ->
                assertIs<NetworkResponse.Success<AcmeDirectoriesResponse>>(actual)
                assertEquals(expected, actual.value)
            }

    suspend fun acmeDirectoriesConnectNoInternet(coreLogic: CoreLogic) = coreLogic.getGlobalScope().unboundNetworkContainer
        .acmeApi.getACMEDirectories(ACME_BASE_URL).also { actual ->
            assertIs<NetworkResponse.Error>(actual)
            assertIs<KaliumException.NoNetwork>(actual.kException.cause)
        }

    /**
     * URL Paths
     */
    private val ACME_BASE_URL = Url("https://balderdash.hogwash.work:9000/acme/google-android/")
    private const val ACME_DIRECTORIES_PATH = "https://balderdash.hogwash.work:9000/acme/google-android/directory"

    /**
     * JSON Response
     */
    private val ACME_DIRECTORIES_RESPONSE = ACMEApiResponseJsonSample.validAcmeDirectoriesResponse
    private val ACME_DIRECTORIES_SAMPLE = ACMEApiResponseJsonSample.ACME_DIRECTORIES_SAMPLE

    /**
     * Request / Responses
     */

    val acmeGetDirectoriesRequestSuccess = MockUnboundNetworkClient.TestRequestHandler(
        path = ACME_DIRECTORIES_PATH,
        httpMethod = HttpMethod.Get,
        responseBody = ACME_DIRECTORIES_RESPONSE.rawJson,
        statusCode = HttpStatusCode.OK
    )
}

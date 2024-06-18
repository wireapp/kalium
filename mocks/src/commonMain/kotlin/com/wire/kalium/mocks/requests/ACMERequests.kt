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
package com.wire.kalium.mocks.requests

import com.wire.kalium.mocks.responses.ACMEApiResponseJsonSample
import com.wire.kalium.network.utils.MockUnboundNetworkClient
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode

object ACMERequests  {

    /**
     * URL Paths
     */
    const val ACME_BASE_URL = "https://balderdash.hogwash.work:9000/acme/google-android/"
    private const val ACME_DIRECTORIES_PATH = "https://balderdash.hogwash.work:9000/acme/google-android/directory"

    /**
     * JSON Response
     */
    private val ACME_DIRECTORIES_RESPONSE = ACMEApiResponseJsonSample.validAcmeDirectoriesResponse
    private val ACME_DIRECTORIES_SAMPLE = ACMEApiResponseJsonSample.ACME_DIRECTORIES_SAMPLE

    /**
     * Request / Responses
     */

    val acmeGetDirectoriesSuccess = MockUnboundNetworkClient.TestRequestHandler(
        path = ACME_DIRECTORIES_PATH,
        httpMethod = HttpMethod.Get,
        responseBody = ACME_DIRECTORIES_RESPONSE.rawJson,
        statusCode = HttpStatusCode.OK
    )
}

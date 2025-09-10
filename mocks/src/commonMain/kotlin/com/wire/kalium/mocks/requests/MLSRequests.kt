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
package com.wire.kalium.mocks.requests

import com.wire.kalium.mocks.responses.CommonResponses
import com.wire.kalium.mocks.responses.MLSPublicKeysResponseJson
import com.wire.kalium.network.utils.TestRequestHandler
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode

object MLSRequests {
    const val PATH_PUBLIC_KEYS = "public-keys"
    const val PATH_MLS = "mls"
    private const val PATH_MLS_KEYS = "${CommonResponses.BASE_PATH_V8}$PATH_MLS/$PATH_PUBLIC_KEYS"

    private val getMLSPublicKeysRequestSuccess = TestRequestHandler(
        path = PATH_MLS_KEYS,
        httpMethod = HttpMethod.Get,
        responseBody = MLSPublicKeysResponseJson.valid.rawJson,
        statusCode = HttpStatusCode.OK,
    )

    val mlsRequestResponseSuccess = listOf(
        getMLSPublicKeysRequestSuccess
    )
}

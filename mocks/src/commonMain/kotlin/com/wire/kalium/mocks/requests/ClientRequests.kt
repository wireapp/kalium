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

import com.wire.kalium.mocks.responses.AppVersionBlackListResponseDTOJson
import com.wire.kalium.mocks.responses.ClientResponseJson
import com.wire.kalium.mocks.responses.CommonResponses
import com.wire.kalium.mocks.responses.ListOfClientsResponseJson
import com.wire.kalium.network.utils.TestRequestHandler
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode

object ClientRequests {

    /**
     * URL Paths
     */
    private const val PATH_CLIENTS = "${CommonResponses.BASE_PATH_V1}clients"
    private const val PATH_ACCESS = "${CommonResponses.BASE_PATH_V1}access"
    private const val PATH_ANDROID = "${CommonResponses.BASE_PATH}android"

    /**
     * Request / Responses
     */
    private val registerClientsRequestSuccess = TestRequestHandler(
        path = PATH_CLIENTS,
        httpMethod = HttpMethod.Post,
        responseBody = ClientResponseJson.valid.rawJson,
        statusCode = HttpStatusCode.OK,
    )
    private val getClientsRequestSuccess = TestRequestHandler(
        path = PATH_CLIENTS,
        httpMethod = HttpMethod.Get,
        responseBody = ListOfClientsResponseJson.valid.rawJson,
        statusCode = HttpStatusCode.OK,
    )
    private val accessApiRequestSuccess = TestRequestHandler(
        path = PATH_ACCESS,
        httpMethod = HttpMethod.Post,
        responseBody = CommonResponses.VALID_ACCESS_TOKEN_RESPONSE,
        statusCode = HttpStatusCode.OK,
    )

    private val appVersionApiRequestSuccess = TestRequestHandler(
        path = PATH_ANDROID,
        httpMethod = HttpMethod.Get,
        responseBody = AppVersionBlackListResponseDTOJson.validAndroid,
        statusCode = HttpStatusCode.OK,
    )

    val clientRequestResponseSuccess = listOf(
        registerClientsRequestSuccess,
        getClientsRequestSuccess,
        accessApiRequestSuccess,
        appVersionApiRequestSuccess
    )
}

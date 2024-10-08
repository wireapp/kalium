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

import com.wire.kalium.mocks.mocks.domain.DomainMocks
import com.wire.kalium.mocks.responses.CommonResponses
import com.wire.kalium.mocks.responses.ValidJsonProvider
import com.wire.kalium.network.api.authenticated.prekey.PreKeyDTO
import com.wire.kalium.network.utils.TestRequestHandler
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode

object PreKeyRequests {
    private const val PATH_CLIENTS_PRE_KEYS = "${CommonResponses.BASE_PATH_V1}clients/defkrr8e7grgsoufhg8/prekeys"
    private const val PATH_CLIENTS = "${CommonResponses.BASE_PATH_V1}clients/defkrr8e7grgsoufhg8"

    private const val PATH_USERS_PRE_KEYS = "${CommonResponses.BASE_PATH_V1}users/list-prekeys"

    private const val USER_1 = "someValue"
    private const val USER_1_CLIENT = "defkrr8e7grgsoufhg8"
    private val USER_1_CLIENT_PREYKEY = PreKeyDTO(key = "preKey1CoQBYIOjl7hw0D8YRNq", id = 1)

    private val clientPreKeysApiRequestSuccess = TestRequestHandler(
        path = PATH_CLIENTS_PRE_KEYS,
        httpMethod = HttpMethod.Get,
        responseBody = "[1]",
        statusCode = HttpStatusCode.OK,
    )
    private val putClientPreKeysApiRequestSuccess = TestRequestHandler(
        path = PATH_CLIENTS,
        httpMethod = HttpMethod.Put,
        responseBody = "",
        statusCode = HttpStatusCode.OK,
    )

    private val jsonProvider = { _: Map<String, Map<String, Map<String, PreKeyDTO?>>> ->
        """
            |{
            |  "${DomainMocks.domain}": {
            |    "$USER_1": {
            |      "$USER_1_CLIENT": {
            |        "key": "${USER_1_CLIENT_PREYKEY.key}",
            |        "id": ${USER_1_CLIENT_PREYKEY.id}
            |      }
            |    }
            |  }
            |}  
        """.trimMargin()
    }

    val valid = ValidJsonProvider(
        mapOf(
            DomainMocks.domain to
                    mapOf(
                        USER_1 to
                                mapOf(USER_1_CLIENT to USER_1_CLIENT_PREYKEY)
                    )
        ),
        jsonProvider
    )

    private val postUserPreKeysApiRequestSuccess = TestRequestHandler(
        path = PATH_USERS_PRE_KEYS,
        httpMethod = HttpMethod.Post,
        responseBody = valid.rawJson,
        statusCode = HttpStatusCode.OK,
    )

    val preKeyRequestResponseSuccess = listOf(
        clientPreKeysApiRequestSuccess,
        putClientPreKeysApiRequestSuccess,
        postUserPreKeysApiRequestSuccess
    )
}

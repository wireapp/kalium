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
package com.wire.kalium.network.utils

import com.wire.kalium.network.api.base.unbound.configuration.ApiVersionDTO
import com.wire.kalium.network.api.base.unbound.configuration.ServerConfigDTO
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode

/**
 * Creates a mock Ktor Http client
 * @param responseBody the response body as a ByteArray
 * @param statusCode the response http status code
 * @param assertion lambda function to apply assertions to the request
 * @return mock Ktor http client
 */
class TestRequestHandler(
    val path: String,
    val responseBody: String,
    val statusCode: HttpStatusCode,
    val assertion: (HttpRequestData.() -> Unit) = {},
    val headers: Map<String, String>? = null,
    val httpMethod: HttpMethod? = null
) {

    companion object {
        val TEST_BACKEND_CONFIG =
            ServerConfigDTO(
                id = "id",
                ServerConfigDTO.Links(
                    "https://test.api.com",
                    "https://test.account.com",
                    "https://test.ws.com",
                    "https://test.blacklist",
                    "https://test.teams.com",
                    "https://test.wire.com",
                    "Test Title",
                    false,
                    null
                ),
                ServerConfigDTO.MetaData(
                    false,
                    ApiVersionDTO.Valid(1),
                    null
                )
            )

        val TEST_BACKEND_LINKS =
            ServerConfigDTO.Links(
                "https://test.api.com",
                "https://test.account.com",
                "https://test.ws.com",
                "https://test.blacklist",
                "https://test.teams.com",
                "https://test.wire.com",
                "Test Title",
                false,
                null
            )

        val TEST_BACKEND =
            ServerConfigDTO(
                id = "id",
                links = TEST_BACKEND_LINKS,
                metaData = ServerConfigDTO.MetaData(
                    false,
                    ApiVersionDTO.Valid(0),
                    domain = null
                )
            )
    }
}

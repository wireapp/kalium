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
package com.wire.kalium.network.api.vcommon

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.WildCardApi
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.HttpMethod

internal class WildCardApiImpl(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient
) : WildCardApi {
    override suspend fun customRequest(
        httpMethod: HttpMethod,
        requestPath: List<String>,
        body: String?,
        queryParam: Map<String, String>,
        customHeader: Map<String, String>
    ): NetworkResponse<String> = wrapKaliumResponse {
        authenticatedNetworkClient.httpClient.request {
            method = httpMethod
            url(requestPath.joinToString("/"))
            body?.let { setBody(it) }
            queryParam.forEach { (key, value) ->
                parameter(key, value)
            }
            customHeader.forEach { (key, value) ->
                headers.append(key, value)
            }
        }
    }
}

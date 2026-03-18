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

package com.wire.kalium.network.api.v15.unauthenticated

import com.wire.kalium.network.UnauthenticatedNetworkClient
import com.wire.kalium.network.api.unauthenticated.sso.InitiateParam
import com.wire.kalium.network.api.v14.unauthenticated.SSOLoginApiV14
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.wrapRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.head
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.appendPathSegments

internal open class SSOLoginApiV15 internal constructor(
    unauthenticatedNetworkClient: UnauthenticatedNetworkClient
) : SSOLoginApiV14(unauthenticatedNetworkClient) {
    override suspend fun initiate(param: InitiateParam): NetworkResponse<String> = HttpRequestBuilder().apply {
        url.appendPathSegments(PATH_SSO, PATH_INITIATE, param.uuid)
        param.label?.let { parameter(QUERY_LABEL, it) }
        if (param is InitiateParam.WithRedirect) {
            parameter(QUERY_SUCCESS_REDIRECT, param.success)
            parameter(QUERY_ERROR_REDIRECT, param.error)
        }
        accept(ContentType.Text.Plain)
    }.let { httpRequestBuilder ->
        val httpRequest = httpClient.head(httpRequestBuilder)
        val url = httpRequest.call.request.url.toString()
        wrapRequest<String> { httpRequest }.mapSuccess {
            url
        }
    }
}

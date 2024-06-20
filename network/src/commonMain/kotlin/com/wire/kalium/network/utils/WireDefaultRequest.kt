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

import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.shouldAddApiVersion
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.encodedPath

fun HttpClientConfig<*>.installWireDefaultRequest(serverConfigDTO: ServerConfigDTO) {
    defaultRequest {
        header(HttpHeaders.ContentType, ContentType.Application.Json)
        with(serverConfigDTO) {
            val apiBaseUrl = Url(links.api)
            // enforce https as url protocol
            url.protocol = URLProtocol.HTTPS
            // add the default host
            url.host = apiBaseUrl.host
            // for api version 0 no api version should be added to the request
            url.encodedPath =
                if (shouldAddApiVersion(metaData.commonApiVersion.version))
                    apiBaseUrl.encodedPath + "v${metaData.commonApiVersion.version}/"
                else apiBaseUrl.encodedPath
        }
    }
}

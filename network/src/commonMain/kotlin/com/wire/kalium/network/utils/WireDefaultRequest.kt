package com.wire.kalium.network.utils

import com.wire.kalium.network.shouldAddApiVersion
import com.wire.kalium.network.tools.ServerConfigDTO
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.encodedPath

fun HttpClientConfig<*>.installWireDefaultRequest(
    serverConfigDTO: ServerConfigDTO
) {
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

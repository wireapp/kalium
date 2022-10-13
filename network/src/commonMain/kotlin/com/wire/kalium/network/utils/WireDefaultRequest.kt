package com.wire.kalium.network.utils

import com.wire.kalium.network.api.base.model.ProxyCredentialsDTO
import com.wire.kalium.network.shouldAddApiVersion
import com.wire.kalium.network.tools.ServerConfigDTO
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.encodedPath
import io.ktor.util.encodeBase64
import io.ktor.utils.io.core.toByteArray

fun HttpClientConfig<*>.installWireDefaultRequest(
    serverConfigDTO: ServerConfigDTO,
    proxyCredentials: (() -> ProxyCredentialsDTO?)? = null
) {
    val isProxyRequired = serverConfigDTO.links.proxy != null

    if (isProxyRequired) {
        if (proxyCredentials == null) throw error("Credentials does not exist")
        engine {
            proxy = serverConfigDTO.links.proxy?.apiProxy?.let { ProxyBuilder.socks(host = it, port = 1080) }
        }
    }

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

            if (isProxyRequired) {
                if (proxyCredentials == null) throw error("Credentials does not exist")
                val (username, password) = proxyCredentials()!!
                val credentials = "$username:$password".toByteArray().encodeBase64()
                header(HttpHeaders.ProxyAuthorization, "Basic $credentials")
            }
        }
    }
}

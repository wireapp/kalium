package com.wire.kalium.network.api.call

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.parameter
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.ContentType

class CallApiImpl internal constructor(private val authenticatedNetworkClient: AuthenticatedNetworkClient) : CallApi {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun getCallConfig(limit: Int?): NetworkResponse<String> =
        wrapKaliumResponse {
            httpClient.get("/$PATH_CALLS/$PATH_CONFIG") {
                limit?.let { parameter(QUERY_KEY_LIMIT, it) }
            }
        }

    override suspend fun connectToSFT(url: String, data: String): NetworkResponse<ByteArray> =
        wrapKaliumResponse {
            httpClient.post(urlString = url) {
                header(HttpHeaders.Accept, ContentType.Application.Json)
                setBody(data)
            }
        }

    private companion object {
        const val PATH_CALLS = "calls"
        const val PATH_CONFIG = "config/v2"

        const val QUERY_KEY_LIMIT = "limit"
    }
}

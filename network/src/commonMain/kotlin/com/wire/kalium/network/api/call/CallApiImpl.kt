package com.wire.kalium.network.api.call

import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class CallApiImpl(private val httpClient: HttpClient) : CallApi {

    override suspend fun getCallConfig(limit: Int?): NetworkResponse<String> =
        wrapKaliumResponse {
            httpClient.get("/$PATH_CALLS/$PATH_CONFIG") {
                limit?.let { parameter(QUERY_KEY_LIMIT, it) }
            }
        }

    private companion object {
        const val PATH_CALLS = "calls"
        const val PATH_CONFIG = "config/v2"

        const val QUERY_KEY_LIMIT = "limit"
    }
}

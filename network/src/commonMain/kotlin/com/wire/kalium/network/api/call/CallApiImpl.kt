package com.wire.kalium.network.api.call

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody

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
                // We are parsing the data string to json due to Ktor serialization escaping the string
                // and thus backend not recognizing and returning a 400 - Bad Request
                val json = KtxSerializer.json.parseToJsonElement(data)
                setBody(json)
            }
        }

    private companion object {
        const val PATH_CALLS = "calls"
        const val PATH_CONFIG = "config/v2"

        const val QUERY_KEY_LIMIT = "limit"
    }
}

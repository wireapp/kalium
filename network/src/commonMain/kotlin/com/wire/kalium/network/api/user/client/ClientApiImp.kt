package com.wire.kalium.network.api.user.client

import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.*
import io.ktor.client.request.*

class ClientApiImp(private val httpClient: HttpClient) : ClientApi {
    override suspend fun registerClient(registerClientRequest: RegisterClientRequest): NetworkResponse<RegisterClientResponse> =
        wrapKaliumResponse {
            httpClient.post(path = PATH_CLIENTS) {
                body = registerClientRequest
            }
        }

    private companion object {
        const val PATH_CLIENTS = "clients"
    }
}

package com.wire.kalium.network.api.user.client

import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class ClientApiImp(private val httpClient: HttpClient) : ClientApi {
    override suspend fun registerClient(registerClientRequest: RegisterClientRequest): NetworkResponse<RegisterClientResponse> =
        wrapKaliumResponse {
            httpClient.post(PATH_CLIENTS) {
                setBody(registerClientRequest)
            }
        }

    private companion object {
        const val PATH_CLIENTS = "clients"
    }
}

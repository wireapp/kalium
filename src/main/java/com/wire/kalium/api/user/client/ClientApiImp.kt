package com.wire.kalium.api.user.client

import com.wire.kalium.api.NetworkResponse
import com.wire.kalium.api.wrapKaliumResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

class ClientApiImp(private val httpClient: HttpClient) : ClientApi {
    override suspend fun registerClient(
        registerClientRequest: RegisterClientRequest
    ): NetworkResponse<RegisterClientResponse> =
        wrapKaliumResponse {
            httpClient.post<HttpResponse>(path = PATH_CLIENTS) {
                body = registerClientRequest
            }.receive()
        }

    private companion object {
        const val PATH_CLIENTS = "clients"
    }
}

package com.wire.kalium.api.user.client

import com.wire.kalium.api.KaliumHttpResult
import com.wire.kalium.api.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse

class ClientApiImp(private val httpClient: HttpClient) : ClientApi {
    override suspend fun registerClient(
            registerClientRequest: RegisterClientRequest): KaliumHttpResult<RegisterClientResponse> =
            wrapKaliumResponse<RegisterClientResponse> {
                httpClient.post<HttpResponse>(path = PATH_CLIENTS) {
                    body = registerClientRequest
                }.receive()
            }
    private companion object {
        const val PATH_CLIENTS = "clients"
    }
}

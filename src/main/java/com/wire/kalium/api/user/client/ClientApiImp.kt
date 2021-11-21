package com.wire.kalium.api.user.client

import com.wire.kalium.api.KaliumHttpResult
import com.wire.kalium.api.user.client.ClientApi.Companion.PATH_CLIENTS
import com.wire.kalium.api.wrapKaliumResponse
import com.wire.kalium.exceptions.AuthException
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
            }.also {
                if (it.httpStatusCode == 400 or 401) {
                    throw AuthException(code = it.httpStatusCode)
                }
            }
}

package com.wire.kalium.api.user.client

import com.wire.kalium.api.KaliumHttpResult
import com.wire.kalium.api.user.client.ClientApi.Companion.BASE_URL
import com.wire.kalium.api.user.client.ClientApi.Companion.PATH_CLIENTS
import com.wire.kalium.api.wrapKaliumResponse
import com.wire.kalium.exceptions.AuthException
import com.wire.kalium.exceptions.HttpException
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders

class ClientApiImp(private val httpClient: HttpClient) : ClientApi {
    override suspend fun registerClient(
        registerClientRequest: RegisterClientRequest,
        token: String
    ): KaliumHttpResult<RegisterClientResponse> =
        wrapKaliumResponse<RegisterClientResponse> {
            httpClient.post<HttpResponse>(path = PATH_CLIENTS) {
                header(HttpHeaders.Authorization, "Bearer $token")
                body = registerClientRequest
            }.receive()
        }.also {
            if (it.httpStatusCode == 400 or 401) {
                throw AuthException(code = it.httpStatusCode)
            }
        }
}

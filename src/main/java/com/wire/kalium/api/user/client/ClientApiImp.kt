package com.wire.kalium.api.user.client

import com.wire.kalium.api.user.client.ClientApi.Companion.BASE_URL
import com.wire.kalium.api.user.client.ClientApi.Companion.PATH_CLIENTS
import com.wire.kalium.exceptions.HttpException
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders


class ClientApiImp(private val httpClient: HttpClient): ClientApi {
    override suspend fun registerClient(registerClientRequest: RegisterClientRequest, token: String): RegisterClientResponse {
        val response = httpClient.post<HttpResponse>(urlString = "$BASE_URL$PATH_CLIENTS") {
            header(HttpHeaders.Authorization, "Bearer $token")
            body = registerClientRequest
        }
        if (response.status.value == 400 or 401) {
            throw HttpException(code = response.status.value, message = response.status.description)
        }
        return response.receive<RegisterClientResponse>()
    }
}

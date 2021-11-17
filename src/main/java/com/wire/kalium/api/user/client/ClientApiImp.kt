package com.wire.kalium.api.user.client

import com.wire.kalium.api.user.client.ClientApi.Companion.BASE_URL
import com.wire.kalium.api.user.client.ClientApi.Companion.PATH_CLIENTS
import com.wire.kalium.exceptions.HttpException
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class ClientApiImp(private val httpClient: HttpClient): ClientApi {
    override suspend fun registerClient(registerClientRequest: RegisterClientRequest, token: String): RegisterClientResponse {
        val response = httpClient.post<HttpResponse>(urlString = "$BASE_URL$PATH_CLIENTS") {
            header(HttpHeaders.Authorization, "Bearer $token")
            body = registerClientRequest
        }
        if (response.status.value == 400 or 401) {
            throw HttpException(code = response.status.value, message = response.status.description)
        }
        println(response.readText())
        return response.receive<RegisterClientResponse>()
    }
}

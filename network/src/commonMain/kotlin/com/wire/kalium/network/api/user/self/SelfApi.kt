package com.wire.kalium.network.api.user.self

import com.wire.kalium.network.api.KaliumHttpResult
import com.wire.kalium.network.api.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse

class SelfApi(private val httpClient: HttpClient) {

    suspend fun getSelfInfo(): KaliumHttpResult<SelfUserInfoResponse> =
        wrapKaliumResponse {
            httpClient.get<HttpResponse>(path = PATH_SELF).receive()
        }

    private companion object {
        const val PATH_SELF = "self"
    }
}

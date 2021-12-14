package com.wire.kalium.network.api.user.self

import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get

class SelfApi(private val httpClient: HttpClient) {

    suspend fun getSelfInfo(): NetworkResponse<SelfUserInfoResponse> =
        wrapKaliumResponse {
            httpClient.get(path = PATH_SELF)
        }

    private companion object {
        const val PATH_SELF = "self"
    }
}

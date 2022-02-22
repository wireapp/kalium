package com.wire.kalium.network.api.user.self

import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody

class SelfApi(private val httpClient: HttpClient) {

    suspend fun getSelfInfo(): NetworkResponse<SelfUserInfoResponse> =
        wrapKaliumResponse {
            httpClient.get(PATH_SELF)
        }

    suspend fun updateSelf(userUpdateRequest: UserUpdateRequest): NetworkResponse<Unit> =
        wrapKaliumResponse {
            httpClient.put(PATH_SELF) {
                setBody(userUpdateRequest)
            }
        }

    private companion object {
        const val PATH_SELF = "self"
    }
}

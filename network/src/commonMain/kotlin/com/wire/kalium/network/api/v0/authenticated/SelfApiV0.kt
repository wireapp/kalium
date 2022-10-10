package com.wire.kalium.network.api.v0.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.self.ChangeHandleRequest
import com.wire.kalium.network.api.base.authenticated.self.SelfApi
import com.wire.kalium.network.api.base.authenticated.self.UserUpdateRequest
import com.wire.kalium.network.api.base.model.UserDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody

internal open class SelfApiV0 internal constructor(private val authenticatedNetworkClient: AuthenticatedNetworkClient) : SelfApi {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun getSelfInfo(): NetworkResponse<UserDTO> = wrapKaliumResponse {
        httpClient.get(PATH_SELF)
    }

    override suspend fun updateSelf(userUpdateRequest: UserUpdateRequest): NetworkResponse<Unit> = wrapKaliumResponse {
        httpClient.put(PATH_SELF) {
            setBody(userUpdateRequest)
        }
    }

    override suspend fun changeHandle(request: ChangeHandleRequest): NetworkResponse<Unit> = wrapKaliumResponse {
        httpClient.put("$PATH_SELF/$PATH_HANDLE") {
            setBody(request)
        }
    }

    private companion object {
        const val PATH_SELF = "self"
        const val PATH_HANDLE = "handle"
    }
}

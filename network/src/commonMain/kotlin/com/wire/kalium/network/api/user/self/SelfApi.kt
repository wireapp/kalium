package com.wire.kalium.network.api.user.self

import com.wire.kalium.network.api.model.UserDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import kotlinx.serialization.SerialName

@kotlinx.serialization.Serializable
data class ChangeHandleRequest(
    @SerialName("handle") val handle: String
)

interface SelfApi {
    suspend fun getSelfInfo(): NetworkResponse<UserDTO>
    suspend fun updateSelf(userUpdateRequest: UserUpdateRequest): NetworkResponse<Unit>
    suspend fun changeHandle(request: ChangeHandleRequest): NetworkResponse<Unit>
}

class SelfApiImpl(private val httpClient: HttpClient) : SelfApi {

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

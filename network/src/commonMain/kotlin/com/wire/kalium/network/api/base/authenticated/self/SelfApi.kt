package com.wire.kalium.network.api.base.authenticated.self

import com.wire.kalium.network.api.base.model.UserDTO
import com.wire.kalium.network.utils.NetworkResponse
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

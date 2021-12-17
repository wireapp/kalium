package com.wire.kalium.network.api.user.client

import com.wire.kalium.network.utils.NetworkResponse

interface ClientApi {

    suspend fun registerClient(registerClientRequest: RegisterClientRequest): NetworkResponse<RegisterClientResponse>
}

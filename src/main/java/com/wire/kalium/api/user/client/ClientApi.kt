package com.wire.kalium.api.user.client

import com.wire.kalium.api.NetworkResponse
import com.wire.kalium.exceptions.HttpException

interface ClientApi {

    @Throws(HttpException::class)
    suspend fun registerClient(registerClientRequest: RegisterClientRequest): NetworkResponse<RegisterClientResponse>
}

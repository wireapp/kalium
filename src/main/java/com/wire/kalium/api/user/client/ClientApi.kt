package com.wire.kalium.api.user.client

import com.wire.kalium.exceptions.HttpException
import com.wire.kalium.tools.HostProvider

interface ClientApi {

    @Throws(HttpException::class)
    suspend fun registerClient(registerClientRequest: RegisterClientRequest, token: String): RegisterClientResponse

    companion object {
        const val BASE_URL = HostProvider.host
        const val PATH_CLIENTS = "/clients"
    }
}

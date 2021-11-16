package com.wire.kalium.api.user.client

import com.wire.kalium.exceptions.HttpException

interface ClientApi {

    @Throws(HttpException::class)
    fun registerClient(registerClientRequest: RegisterClientRequest): RegisterClientResponse

    companion object {
        protected const val PATH_CLIENTS = "clients"
    }
}

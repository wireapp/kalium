package com.wire.kalium.api.user.client

import com.wire.kalium.api.KaliumHttpResult
import com.wire.kalium.exceptions.HttpException
import com.wire.kalium.tools.HostProvider

interface ClientApi {

    @Throws(HttpException::class)
    suspend fun registerClient(registerClientRequest: RegisterClientRequest): KaliumHttpResult<RegisterClientResponse>
}

package com.wire.kalium.network.api.user.client

import com.wire.kalium.network.api.KaliumHttpResult

interface ClientApi {

    suspend fun registerClient(registerClientRequest: RegisterClientRequest): KaliumHttpResult<RegisterClientResponse>
}

package com.wire.kalium.network.api.base.authenticated.connection

import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.utils.NetworkResponse

interface ConnectionApi {

    suspend fun fetchSelfUserConnections(pagingState: String?): NetworkResponse<ConnectionResponse>
    suspend fun createConnection(userId: UserId): NetworkResponse<ConnectionDTO>
    suspend fun updateConnection(userId: UserId, connectionStatus: ConnectionStateDTO): NetworkResponse<ConnectionDTO>
}

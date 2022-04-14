package com.wire.kalium.logic.feature.connection

import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.user.UserRepository

class ConnectionScope(
    private val connectionRepository: ConnectionRepository
) {

    internal val syncConnections: SyncConnectionsUseCase get() = SyncConnectionsUseCaseImpl(
        connectionRepository = connectionRepository
    )
}

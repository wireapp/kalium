package com.wire.kalium.logic.feature.connection

import com.wire.kalium.logic.data.connection.ConnectionRepository

class ConnectionScope(
    private val connectionRepository: ConnectionRepository
) {

    internal val syncConnections: SyncConnectionsUseCase
        get() = SyncConnectionsUseCaseImpl(
            connectionRepository = connectionRepository
        )

    val sendConnectionRequest: SendConnectionRequestUseCase get() = SendConnectionRequestUseCaseImpl(connectionRepository)

    val acceptConnectionRequest: AcceptConnectionRequestUseCase get() = AcceptConnectionRequestUseCaseImpl(connectionRepository)

    val cancelConnectionRequest: CancelConnectionRequestUseCase get() = CancelConnectionRequestUseCaseImpl(connectionRepository)
}

package com.wire.kalium.logic.feature.connection

import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository

class ConnectionScope(
    private val connectionRepository: ConnectionRepository,
    private val conversationRepository: ConversationRepository,
) {

    internal val syncConnections: SyncConnectionsUseCase
        get() = SyncConnectionsUseCaseImpl(
            connectionRepository = connectionRepository
        )

    val sendConnectionRequest: SendConnectionRequestUseCase get() = SendConnectionRequestUseCaseImpl(connectionRepository)

    val acceptConnectionRequest: AcceptConnectionRequestUseCase get() = AcceptConnectionRequestUseCaseImpl(connectionRepository, conversationRepository)

    val cancelConnectionRequest: CancelConnectionRequestUseCase get() = CancelConnectionRequestUseCaseImpl(connectionRepository)

    val ignoreConnectionRequest: IgnoreConnectionRequestUseCase get() = IgnoreConnectionRequestUseCaseImpl(connectionRepository)

}

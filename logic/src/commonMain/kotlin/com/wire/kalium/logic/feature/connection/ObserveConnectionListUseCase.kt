package com.wire.kalium.logic.feature.connection

import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationDetails
import kotlinx.coroutines.flow.Flow

/**
 * Use Case that listen to any user connection changes
 */
fun interface ObserveConnectionListUseCase {
    /**
     * Use case [ObserveConnectionListUseCase] operation
     *
     * @return a [Flow<List<Connection>>] containing all current connections
     */
    suspend operator fun invoke(): Flow<List<ConversationDetails>>
}

internal class ObserveConnectionListUseCaseImpl internal constructor(
    private val connectionRepository: ConnectionRepository,
) : ObserveConnectionListUseCase {

    override suspend operator fun invoke(): Flow<List<ConversationDetails>> {
        return connectionRepository.observeConnectionList()
    }
}

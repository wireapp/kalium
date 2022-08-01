package com.wire.kalium.logic.feature.connection

import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

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

internal class ObserveConnectionListUseCaseImpl(
    private val connectionRepository: ConnectionRepository,
    private val syncManager: SyncManager
) : ObserveConnectionListUseCase {

    override suspend operator fun invoke(): Flow<List<ConversationDetails>> {
        syncManager.startSyncIfIdle()
        return connectionRepository.observeConnectionList().distinctUntilChanged()
    }
}

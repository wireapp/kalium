package com.wire.kalium.logic.feature.connection

import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    suspend operator fun invoke(): Flow<List<Connection>>
}

internal class ObserveConnectionListUseCaseImpl(
    private val connectionRepository: ConnectionRepository,
    private val syncManager: SyncManager
): ObserveConnectionListUseCase {

    //TODO [AR-1732]
    override suspend operator fun invoke(): Flow<List<Connection>> {
        syncManager.waitForSlowSyncToComplete()
        return connectionRepository.observeConnectionList()
    }
}

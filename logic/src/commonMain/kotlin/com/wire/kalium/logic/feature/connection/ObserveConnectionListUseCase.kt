package com.wire.kalium.logic.feature.connection

import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveConnectionListUseCase(
    private val connectionRepository: ConnectionRepository,
    private val syncManager: SyncManager
) {

    //TODO [AR-1732]
    suspend operator fun invoke(): Flow<List<Connection>> {
        syncManager.waitForSlowSyncToComplete()
        return connectionRepository.observeConnectionList()
    }
}

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.feature.message.AddSystemMessageToAllConversationsUseCase

internal interface ConversationsRecoveryManager {
    suspend fun invoke()
}

internal class ConversationsRecoveryManagerImpl(
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val addSystemMessageToAllConversationsUseCase: AddSystemMessageToAllConversationsUseCase,
    private val slowSyncRepository: SlowSyncRepository,
) : ConversationsRecoveryManager {

    @Suppress("ComplexCondition")
    override suspend fun invoke() {
        // wait until incremental sync is done
        incrementalSyncRepository.incrementalSyncState.collect { syncState ->
            if (syncState is IncrementalSyncStatus.Live &&
                slowSyncRepository.needsToPersistHistoryLostMessage()
            ) {
                addSystemMessageToAllConversationsUseCase.invoke()
                slowSyncRepository.setNeedsToPersistHistoryLostMessage(false)
            }
        }
    }

}

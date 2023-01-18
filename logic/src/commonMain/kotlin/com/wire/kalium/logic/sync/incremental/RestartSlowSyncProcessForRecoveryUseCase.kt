package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.logic.data.sync.SlowSyncRepository

/**
 * Restart slowSync process for recovery.
 */
interface RestartSlowSyncProcessForRecoveryUseCase {
    suspend operator fun invoke()
}

class RestartSlowSyncProcessForRecoveryUseCaseImpl internal constructor(
    private val slowSyncRepository: SlowSyncRepository
) : RestartSlowSyncProcessForRecoveryUseCase {
    override suspend operator fun invoke() {
        slowSyncRepository.clearLastSlowSyncCompletionInstant()
        slowSyncRepository.setNeedsToRecoverMLSGroups(true)
        slowSyncRepository.setNeedsToPersistHistoryLostMessage(true)
    }
}

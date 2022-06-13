package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.self.SelfUserRepository
import com.wire.kalium.logic.sync.SyncManager

class UpdateSelfAvailabilityStatusUseCase(
    private val selfUserRepository: SelfUserRepository,
    private val syncManager: SyncManager
) {
    suspend operator fun invoke(status: UserAvailabilityStatus) {
        if (syncManager.isSlowSyncOngoing()) {
            syncManager.waitUntilLive()
        }
        selfUserRepository.updateSelfUserAvailabilityStatus(status)
    }
}

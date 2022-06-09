package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.auth.ValidateUserHandleUseCase
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity

class UpdateSelfAvailabilityStatusUseCase(
    private val userRepository: UserRepository,
    private val syncManager: SyncManager
) {
    suspend fun invoke(status: UserAvailabilityStatusEntity) {
        if (syncManager.isSlowSyncOngoing()) {
            syncManager.waitUntilLive()
        }
        userRepository.updateSelfUserAvailabilityStatus(status)
    }
}

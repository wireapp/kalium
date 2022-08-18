package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.sync.SyncManager

class UpdateSelfAvailabilityStatusUseCase(
    private val userRepository: UserRepository,
    private val syncManager: SyncManager
) {
    suspend operator fun invoke(status: UserAvailabilityStatus) {
        // TODO: Handle possibility of being offline. Storing the broadcast to be sent when Sync is done.
        //       For now, any "offline failure" can be ignored, as we do not broadcast the availability to other devices or users.
        syncManager.waitUntilLiveOrFailure()
        userRepository.updateSelfUserAvailabilityStatus(status)
    }
}

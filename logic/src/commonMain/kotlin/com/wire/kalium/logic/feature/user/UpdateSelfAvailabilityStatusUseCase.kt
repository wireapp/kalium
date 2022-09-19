package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserRepository

class UpdateSelfAvailabilityStatusUseCase internal constructor(
    private val userRepository: UserRepository,
) {
    suspend operator fun invoke(status: UserAvailabilityStatus) {
        // TODO: Handle possibility of being offline. Storing the broadcast to be sent when Sync is done.
        //       For now, we don't need Sync, as we do not broadcast the availability to other devices or users.
        userRepository.updateSelfUserAvailabilityStatus(status)
    }
}

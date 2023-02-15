package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.configuration.UserConfigRepository

/**
 * Mark the file sharing status as notified
 * need to be called after notifying the user about the change
 * e.g. after showing a dialog, or a toast etc.
 */
class MarkFileSharingChangeAsNotifiedUseCase(
    private val userConfigRepository: UserConfigRepository
) {
    suspend operator fun invoke() {
        userConfigRepository.setFileSharingAsNotified()
    }
}

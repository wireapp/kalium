package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.configuration.UserConfigRepository

class MarkFileSharingChangeAsNotifiedUseCase(
    private val userConfigRepository: UserConfigRepository
) {
    operator suspend fun invoke() {
        userConfigRepository.setIsChangedAsFalse()
    }
}

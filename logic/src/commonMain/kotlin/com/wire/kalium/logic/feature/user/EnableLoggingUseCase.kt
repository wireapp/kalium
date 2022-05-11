package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.configuration.UserConfigRepository


class EnableLoggingUseCase(
    private val userConfigRepository: UserConfigRepository
) {

    operator fun invoke(enabled: Boolean) =
        userConfigRepository.persistEnableLogging(enabled)
}




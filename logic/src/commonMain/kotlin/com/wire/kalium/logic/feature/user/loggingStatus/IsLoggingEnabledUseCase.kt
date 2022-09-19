package com.wire.kalium.logic.feature.user.loggingStatus

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.fold

interface IsLoggingEnabledUseCase {
    suspend operator fun invoke(): Boolean
}

class IsLoggingEnabledUseCaseImpl(
    private val userConfigRepository: UserConfigRepository
) : IsLoggingEnabledUseCase {
    override suspend operator fun invoke(): Boolean =
        userConfigRepository.isLoggingEnabled().fold({
            false
        }, {
            it
        })
}

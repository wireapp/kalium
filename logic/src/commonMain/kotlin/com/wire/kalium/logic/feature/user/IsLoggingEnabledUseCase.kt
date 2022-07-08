package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.fold

interface IsLoggingEnabledUseCase {
    operator fun invoke(): Boolean
}


class IsLoggingEnabledUseCaseImpl(
    private val userConfigRepository: UserConfigRepository
) : IsLoggingEnabledUseCase {

    override operator fun invoke(): Boolean =
        userConfigRepository.isLoggingEnabled().fold({
            false
        }, {
            it
        })
}

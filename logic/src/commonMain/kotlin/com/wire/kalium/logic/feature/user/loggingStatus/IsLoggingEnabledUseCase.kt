package com.wire.kalium.logic.feature.user.loggingStatus

import com.wire.kalium.logic.configuration.GlobalConfigRepository
import com.wire.kalium.logic.functional.fold

interface IsLoggingEnabledUseCase {
    operator fun invoke(): Boolean
}

class IsLoggingEnabledUseCaseImpl(
    private val globalConfigRepository: GlobalConfigRepository
) : IsLoggingEnabledUseCase {
    override operator fun invoke(): Boolean =
        globalConfigRepository.isLoggingEnabled().fold({
            false
        }, {
            it
        })
}

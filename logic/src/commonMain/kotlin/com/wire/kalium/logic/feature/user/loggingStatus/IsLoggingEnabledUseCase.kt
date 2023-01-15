package com.wire.kalium.logic.feature.user.loggingStatus

import com.wire.kalium.logic.configuration.GlobalConfigRepository
import com.wire.kalium.logic.functional.fold

/**
 * Checks if logging is enabled for the current user.
 */
interface IsLoggingEnabledUseCase {
    /**
     * @return true if logging is enabled, false otherwise.
     */
    operator fun invoke(): Boolean
}

internal class IsLoggingEnabledUseCaseImpl(
    private val globalConfigRepository: GlobalConfigRepository
) : IsLoggingEnabledUseCase {
    override operator fun invoke(): Boolean =
        globalConfigRepository.isLoggingEnabled().fold({
            false
        }, {
            it
        })
}

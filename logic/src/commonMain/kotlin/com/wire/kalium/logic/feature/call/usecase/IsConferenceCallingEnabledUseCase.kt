package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.fold

/**
 * @return the [Boolean] for if the user's team has conference calling enabled in its plan.
 */
interface IsConferenceCallingEnabledUseCase {
    operator fun invoke(): Boolean
}

internal class IsConferenceCallingEnabledUseCaseImpl(
    private val userConfigRepository: UserConfigRepository
) : IsConferenceCallingEnabledUseCase {

    override fun invoke(): Boolean =
        userConfigRepository
            .isConferenceCallingEnabled()
            .fold({
                DEFAULT_CONFERENCE_CALLING_ENABLED_VALUE
            }, {
                it
            })

    private companion object {
        const val DEFAULT_CONFERENCE_CALLING_ENABLED_VALUE = false
    }
}

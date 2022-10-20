package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.mapToRightOr
import kotlinx.coroutines.flow.Flow

/**
 * @return the [Boolean] for if the user's team has conference calling enabled in its plan.
 */
interface ObserveIsConferenceCallingEnabledUseCase {
    operator fun invoke(): Flow<Boolean>
}

internal class ObserveIsConferenceCallingEnabledUseCaseImpl(
    private val userConfigRepository: UserConfigRepository
) : ObserveIsConferenceCallingEnabledUseCase {

    override fun invoke(): Flow<Boolean> =
        userConfigRepository
            .isConferenceCallingEnabledFlow()
            .mapToRightOr(DEFAULT_CONFERENCE_CALLING_ENABLED_VALUE)

    private companion object {
        const val DEFAULT_CONFERENCE_CALLING_ENABLED_VALUE = false
    }
}

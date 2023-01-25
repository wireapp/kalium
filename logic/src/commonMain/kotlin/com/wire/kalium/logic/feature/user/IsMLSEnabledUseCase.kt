package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.functional.fold

/**
 * Checks if the current user has enabled MLS support.
 */
interface IsMLSEnabledUseCase {
    /**
     * @return true if MLS is enabled, false otherwise.
     */
    suspend operator fun invoke(): Boolean
}

internal class IsMLSEnabledUseCaseImpl(
    private val featureSupport: FeatureSupport,
    private val userConfigRepository: UserConfigRepository
) : IsMLSEnabledUseCase {

    override suspend operator fun invoke(): Boolean =
        userConfigRepository.isMLSEnabled().fold({
            false
        }, {
            it && featureSupport.isMLSSupported
        })
}

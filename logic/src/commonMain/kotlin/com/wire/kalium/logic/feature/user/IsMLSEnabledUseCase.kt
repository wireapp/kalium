package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.fold

interface IsMLSEnabledUseCase {

    suspend operator fun invoke(): Boolean

}

class IsMLSEnabledUseCaseImpl(
    private val userConfigRepository: UserConfigRepository
) : IsMLSEnabledUseCase {

    override suspend operator fun invoke(): Boolean =
        userConfigRepository.isMLSEnabled().fold({
            false
        }, {
            it
        })
}

package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.Either

interface EnableLoggingUseCase {
    operator fun invoke(enabled: Boolean): Either<StorageFailure, Unit>
}

class EnableLoggingUseCaseImpl(
    private val userConfigRepository: UserConfigRepository
) : EnableLoggingUseCase {

    override operator fun invoke(enabled: Boolean) = userConfigRepository.persistEnableLogging(enabled)
}

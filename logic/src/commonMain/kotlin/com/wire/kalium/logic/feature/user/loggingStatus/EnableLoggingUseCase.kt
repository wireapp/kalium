package com.wire.kalium.logic.feature.user.loggingStatus

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.Either

interface EnableLoggingUseCase {
    suspend operator fun invoke(enabled: Boolean): Either<StorageFailure, Unit>
}

class EnableLoggingUseCaseImpl(
    private val userConfigRepository: UserConfigRepository
) : EnableLoggingUseCase {
    override suspend operator fun invoke(enabled: Boolean) = userConfigRepository.persistEnableLogging(enabled)
}

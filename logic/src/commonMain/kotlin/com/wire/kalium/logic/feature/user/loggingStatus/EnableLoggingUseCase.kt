package com.wire.kalium.logic.feature.user.loggingStatus

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.GlobalConfigRepository
import com.wire.kalium.logic.functional.Either

interface EnableLoggingUseCase {
    operator fun invoke(enabled: Boolean): Either<StorageFailure, Unit>
}

class EnableLoggingUseCaseImpl(
    private val globalConfigRepository: GlobalConfigRepository
) : EnableLoggingUseCase {
    override operator fun invoke(enabled: Boolean) = globalConfigRepository.persistEnableLogging(enabled)
}

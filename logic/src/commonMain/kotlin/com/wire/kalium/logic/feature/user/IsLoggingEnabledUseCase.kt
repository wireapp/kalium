package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.Either

class IsLoggingEnabledUseCase(
    private val userConfigRepository: UserConfigRepository
) {

    operator fun invoke(): Either<StorageFailure, Boolean> =
        userConfigRepository.isLoggingEnabled().fold({
            when (it) {
                StorageFailure.DataNotFound -> Either.Right(false)
                is StorageFailure.Generic -> Either.Left(it)
            }
        }, {
            Either.Right(it)
        })
}


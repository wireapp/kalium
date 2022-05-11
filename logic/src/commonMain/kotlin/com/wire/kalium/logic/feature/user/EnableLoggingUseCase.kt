package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository


class EnableLoggingUseCase(
    private val userConfigRepository: UserConfigRepository
) {

    operator fun invoke(enabled: Boolean): Result =
        userConfigRepository.persistEnableLogging(enabled).fold({
            Result.Failure.Generic(it)
        }, {
            Result.Success
        })


    sealed class Result {
        object Success : Result()
        sealed class Failure : Result() {
            class Generic(val failure: StorageFailure) : Failure()
        }
    }
}




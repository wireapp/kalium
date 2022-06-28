package com.wire.kalium.logic.feature.user


import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.fold

/**
 */

interface SetFileSharingStatusUseCase {
    operator fun invoke(isFileSharingEnabled: Boolean, isStatusChanged: Boolean?): Result
}

class SetFileSharingStatusUseCaseImpl(
    private val userConfigRepository: UserConfigRepository
) : SetFileSharingStatusUseCase {

    override operator fun invoke(isFileSharingEnabled: Boolean, isStatusChanged: Boolean?): Result =
        userConfigRepository.setFileSharingStatus(isFileSharingEnabled, isStatusChanged).fold({
            Result.Failure.Generic(it)
        }, {
            Result.Success
        })
}


sealed class Result {
    object Success : Result()
    sealed class Failure : Result() {
        class Generic(val failure: StorageFailure) : Failure()
    }
}


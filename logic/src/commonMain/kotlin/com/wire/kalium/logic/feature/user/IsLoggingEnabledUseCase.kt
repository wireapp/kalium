package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger

interface IsLoggingEnabledUseCase {
    operator fun invoke(): Boolean
}


class IsLoggingEnabledUseCaseImpl(
    private val userConfigRepository: UserConfigRepository
) : IsLoggingEnabledUseCase {

    override operator fun invoke(): Boolean =
        userConfigRepository.isLoggingEnabled().fold({
            when (it) {
                StorageFailure.DataNotFound -> {
                    kaliumLogger.e("Data not found")
                    false
                }
                is StorageFailure.Generic -> {
                    kaliumLogger.e("Storage Error : ${it.rootCause}")
                    false
                }
            }
        }, {
            it
        })

}


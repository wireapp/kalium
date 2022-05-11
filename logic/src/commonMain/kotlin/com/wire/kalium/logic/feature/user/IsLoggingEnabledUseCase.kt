package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.kaliumLogger

class IsLoggingEnabledUseCase(
    private val userConfigRepository: UserConfigRepository
) {

    operator fun invoke(): Boolean =
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


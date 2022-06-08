package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger

interface IsFileSharingEnabledUseCase {
    operator fun invoke(): Boolean
}

class IsFileSharingEnabledUseCaseImpl(
    private val userConfigRepository: UserConfigRepository
) : IsFileSharingEnabledUseCase {

    override operator fun invoke(): Boolean =
        userConfigRepository.isFileSharingEnabled().fold({
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


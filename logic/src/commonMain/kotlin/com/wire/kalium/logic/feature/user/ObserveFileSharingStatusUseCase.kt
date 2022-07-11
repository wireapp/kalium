package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * This use case is to get a flow of  file sharing status of the team settings from the local storage
 * so we can use it to show and hide things on the screen
 */

interface ObserveFileSharingStatusUseCase {
    operator fun invoke(): Flow<FileSharingStatus>
}

class ObserveFileSharingStatusUseCaseImpl(private val userConfigRepository: UserConfigRepository) : ObserveFileSharingStatusUseCase {
    override operator fun invoke(): Flow<FileSharingStatus> =
        userConfigRepository.isFileSharingEnabledFlow().map { fileSharingStatusFlow ->
            fileSharingStatusFlow.fold({
                when (it) {
                    StorageFailure.DataNotFound -> {
                        kaliumLogger.e("Data not found in ObserveFileSharingStatusUseCase")
                        FileSharingStatus(null, null)
                    }
                    is StorageFailure.Generic -> {
                        kaliumLogger.e("Storage Error : ${it.rootCause} in ObserveFileSharingStatusUseCase", it.rootCause)
                        FileSharingStatus(null, null)
                    }
                }
            }, {
                it
            })
        }
}

package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.FileSharingEntity
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.fold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


/**
 * This use case is to get a flow of  file sharing status of the team settings from the local storage
 * so we can use it to show and hide things on the screen
 */

interface FileSharingStatusFlowUseCase {
    operator fun invoke(): Flow<FileSharingEntity>
}

class FileSharingStatusFlowUseCaseImpl(private val userConfigRepository: UserConfigRepository) : FileSharingStatusFlowUseCase {
    override operator fun invoke(): Flow<FileSharingEntity> =
        userConfigRepository.isFileSharingEnabledFlow().map { fileSharingStatusFlow ->
            fileSharingStatusFlow.fold({
                when (it) {
                    StorageFailure.DataNotFound -> {
                        FileSharingEntity(null, null)
                    }
                    is StorageFailure.Generic -> {
                        FileSharingEntity(null, null)
                    }
                }
            }, {
                it
            })
        }
}

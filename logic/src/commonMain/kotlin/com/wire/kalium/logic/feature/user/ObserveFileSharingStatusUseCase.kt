/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.common.functional.fold
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

internal class ObserveFileSharingStatusUseCaseImpl(private val userConfigRepository: UserConfigRepository) :
    ObserveFileSharingStatusUseCase {
    override operator fun invoke(): Flow<FileSharingStatus> =
        userConfigRepository.isFileSharingEnabledFlow().map { fileSharingStatusFlow ->
            fileSharingStatusFlow.fold({
                when (it) {
                    StorageFailure.DataNotFound -> {
                        kaliumLogger.e("Data not found in ObserveFileSharingStatusUseCase")
                        FileSharingStatus(FileSharingStatus.Value.Disabled, false)
                    }

                    is StorageFailure.Generic -> {
                        kaliumLogger.e("Storage Error : ${it.rootCause} in ObserveFileSharingStatusUseCase", it.rootCause)
                        FileSharingStatus(FileSharingStatus.Value.Disabled, false)
                    }
                }
            }, {
                it
            })
        }
}

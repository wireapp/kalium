/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger

/**
 * This use case is to get the file sharing status of the team settings from the local storage
 * so we can use it to show and hide things on the screen
 */

interface IsFileSharingEnabledUseCase {
    operator fun invoke(): FileSharingStatus
}

class IsFileSharingEnabledUseCaseImpl(
    private val userConfigRepository: UserConfigRepository
) : IsFileSharingEnabledUseCase {

    override operator fun invoke(): FileSharingStatus =
        userConfigRepository.isFileSharingEnabled().fold({
            when (it) {
                StorageFailure.DataNotFound -> {
                    kaliumLogger.e("Data not found in IsFileSharingEnabledUseCase")
                    FileSharingStatus(null, null)
                }
                is StorageFailure.Generic -> {
                    kaliumLogger.e("Storage Error : ${it.rootCause} in IsFileSharingEnabledUseCase", it.rootCause)
                    FileSharingStatus(null, null)
                }
            }
        }, {
            FileSharingStatus(it.isFileSharingEnabled, it.isStatusChanged)
        })
}

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
package com.wire.kalium.logic.feature.featureConfig.handler

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.AppLockModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.common.functional.Either

class AppLockConfigHandler constructor(
    private val userConfigRepository: UserConfigRepository
) {
    suspend fun handle(appLockConfig: AppLockModel): Either<CoreFailure, Unit> {

        val isStatusChanged = when (val current = userConfigRepository.isTeamAppLockEnabled()) {
            is Either.Left -> if (current.value is StorageFailure.DataNotFound) {
                appLockConfig.status == Status.ENABLED
            } else return current
            is Either.Right -> if (current.value.isEnforced != appLockConfig.status.toBoolean()) {
                true
            } else {
                current.value.isStatusChanged
            }
        }
        return userConfigRepository.setAppLockStatus(
            isAppLocked = appLockConfig.status.toBoolean(),
            timeout = appLockConfig.inactivityTimeoutSecs,
            isStatusChanged = isStatusChanged
        )
    }
}

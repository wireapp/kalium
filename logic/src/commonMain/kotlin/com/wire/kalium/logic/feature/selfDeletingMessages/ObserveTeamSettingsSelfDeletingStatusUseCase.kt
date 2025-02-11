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
package com.wire.kalium.logic.feature.selfDeletingMessages

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.message.TeamSelfDeleteTimer
import com.wire.kalium.logic.data.message.TeamSettingsSelfDeletionStatus
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Use case to observe the status of the self deletion settings set by the team admin.
 */
interface ObserveTeamSettingsSelfDeletingStatusUseCase {
    suspend operator fun invoke(): Flow<TeamSettingsSelfDeletionStatus>
}

class ObserveTeamSettingsSelfDeletingStatusUseCaseImpl internal constructor(
    private val userConfigRepository: UserConfigRepository
) : ObserveTeamSettingsSelfDeletingStatusUseCase {
    override suspend fun invoke(): Flow<TeamSettingsSelfDeletionStatus> =
        userConfigRepository.observeTeamSettingsSelfDeletingStatus().map {
            it.fold(
                {
                    kaliumLogger.e("There was an error when fetching team settings self deletion timer")
                    TeamSettingsSelfDeletionStatus(null, TeamSelfDeleteTimer.Enabled)
                },
                { teamSettingsSelfDeletionStatus ->
                    teamSettingsSelfDeletionStatus
                }
            )
        }

}

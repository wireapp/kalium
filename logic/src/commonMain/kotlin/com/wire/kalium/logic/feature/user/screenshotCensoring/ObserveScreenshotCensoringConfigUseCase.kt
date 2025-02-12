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

package com.wire.kalium.logic.feature.user.screenshotCensoring

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.message.TeamSelfDeleteTimer
import com.wire.kalium.common.functional.mapRight
import com.wire.kalium.common.functional.mapToRightOr
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * UseCase that allow us to get the configuration of screenshot censoring enabled or not
 */
interface ObserveScreenshotCensoringConfigUseCase {
    suspend operator fun invoke(): Flow<ObserveScreenshotCensoringConfigResult>
}

internal class ObserveScreenshotCensoringConfigUseCaseImpl(
    private val userConfigRepository: UserConfigRepository,
) : ObserveScreenshotCensoringConfigUseCase {

    override suspend fun invoke(): Flow<ObserveScreenshotCensoringConfigResult> {
        return combine(
            userConfigRepository.observeScreenshotCensoringConfig()
                .mapToRightOr(false),
            userConfigRepository.observeTeamSettingsSelfDeletingStatus()
                .mapRight { it.enforcedSelfDeletionTimer is TeamSelfDeleteTimer.Enforced }
                .mapToRightOr(false)
        ) { screenshotCensoringEnabled, teamSelfDeletingEnforced ->
            when {
                teamSelfDeletingEnforced -> ObserveScreenshotCensoringConfigResult.Enabled.EnforcedByTeamSelfDeletingSettings
                screenshotCensoringEnabled -> ObserveScreenshotCensoringConfigResult.Enabled.ChosenByUser
                else -> ObserveScreenshotCensoringConfigResult.Disabled
            }
        }
    }
}

sealed class ObserveScreenshotCensoringConfigResult {
    data object Disabled : ObserveScreenshotCensoringConfigResult()
    sealed class Enabled : ObserveScreenshotCensoringConfigResult() {
        data object ChosenByUser : Enabled()
        data object EnforcedByTeamSelfDeletingSettings : Enabled()
    }
}

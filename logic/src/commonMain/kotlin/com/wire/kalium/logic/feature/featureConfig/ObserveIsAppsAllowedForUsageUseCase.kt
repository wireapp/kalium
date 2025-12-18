/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.featureConfig

import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.mapToRightOr
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import io.mockative.Mockable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * This use case is responsible for observing the apps enabled configuration.
 * Based on the feature config and if the user belongs to a team or not.
 *
 * It returns a boolean indicating whether the apps feature is enabled or not for this user session.
 * In case the team or session does not have the apps feature enabled, it will return false.
 */
@Mockable
internal interface ObserveIsAppsAllowedForUsageUseCase {
    suspend operator fun invoke(): Flow<Boolean>
}

internal class ObserveIsAppsAllowedForUsageUseCaseImpl(
    private val userConfigRepository: UserConfigRepository,
    private val selfTeamIdProvider: SelfTeamIdProvider
) : ObserveIsAppsAllowedForUsageUseCase {
    override suspend fun invoke(): Flow<Boolean> = userConfigRepository.observeAppsEnabled()
        .mapToRightOr(false)
        .map { appsEnabled ->
            val belongsToTeam = selfTeamIdProvider().getOrNull() != null
            appsEnabled && belongsToTeam
        }
}

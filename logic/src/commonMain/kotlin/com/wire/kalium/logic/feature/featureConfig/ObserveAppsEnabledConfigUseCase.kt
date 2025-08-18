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

import com.wire.kalium.common.functional.mapToRightOr
import com.wire.kalium.logic.configuration.UserConfigRepository
import io.mockative.Mockable
import kotlinx.coroutines.flow.Flow

/**
 * This use case is responsible for observing the apps enabled configuration.
 * It returns a boolean indicating whether the apps feature is enabled or not for this user session.
 * In case the team or session does not have the apps feature enabled, it will return false.
 */
@Mockable
interface ObserveAppsEnabledConfigUseCase {
    suspend operator fun invoke(): Flow<Boolean>
}

internal class ObserveAppsEnabledConfigUseCaseImpl(
    private val userConfigRepository: UserConfigRepository
) : ObserveAppsEnabledConfigUseCase {
    override suspend fun invoke(): Flow<Boolean> = userConfigRepository.observeAppsEnabled()
        .mapToRightOr(false)
}

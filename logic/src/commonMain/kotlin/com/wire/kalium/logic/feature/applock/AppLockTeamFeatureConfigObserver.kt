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
package com.wire.kalium.logic.feature.applock

import com.wire.kalium.logic.configuration.AppLockTeamConfig
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.functional.fold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.seconds

/**
 * observe app lock feature flag of the team
 */
interface AppLockTeamFeatureConfigObserver {
    operator fun invoke(): Flow<AppLockTeamConfig>
}

class AppLockTeamFeatureConfigObserverImpl(
    private val userConfigRepository: UserConfigRepository,
    private val kaliumConfigs: KaliumConfigs
) : AppLockTeamFeatureConfigObserver {
    override fun invoke(): Flow<AppLockTeamConfig> {
        if (kaliumConfigs.teamAppLock) {
            return flowOf(
                AppLockTeamConfig(
                    isEnabled = true,
                    timeout = kaliumConfigs.teamAppLockTimeout.seconds,
                    isStatusChanged = false
                )
            )
        }
        return userConfigRepository.observeAppLockConfig().map {
            it.fold({
                AppLockTeamConfig(
                    isEnabled = false,
                    timeout = DEFAULT_TIMEOUT,
                    isStatusChanged = false
                )
            }, { appLock ->
                appLock
            })
        }
    }

    companion object {
        val DEFAULT_TIMEOUT = 60.seconds
    }
}

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
package com.wire.kalium.logic.feature.featureConfig

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.functional.flatMapRight
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.mapToRightOr
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Checks if the app lock is editable.
 * The app lock is editable if there is no enforced app lock on any of the user's accounts.
 * If there is an enforced app lock on any of the user's accounts, the app lock is not editable.
 */
interface ObserveIsAppLockEditableUseCase {
    suspend operator fun invoke(): Flow<Boolean>
}

class ObserveIsAppLockEditableUseCaseImpl internal constructor(
    private val userSessionScopeProvider: UserSessionScopeProvider,
    private val sessionRepository: SessionRepository
) : ObserveIsAppLockEditableUseCase {
    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend operator fun invoke(): Flow<Boolean> =
        sessionRepository.allValidSessionsFlow()
            .flatMapRight { accounts ->
                combine(
                    accounts.map { session ->
                        userSessionScopeProvider.getOrCreate(session.userId) { userConfigRepository }
                            .observeAppLockConfig()
                            .map { appLockConfig ->
                                appLockConfig.fold({ false }, { config -> config.isEnforced })
                            }
                    }
                ) { it.contains(true).not() }
            }
            .mapToRightOr(false)
}

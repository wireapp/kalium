/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.featureFlags.FeatureSupport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Use case that reports whether the meetings feature is enabled for the current user and current API version supports it.
 * @return flow of boolean values indicating whether meetings is enabled for the current user and supported by the current API version.
 */
public interface ObserveIsMeetingsEnabledUseCase {
    public suspend operator fun invoke(): Flow<Boolean>
}

internal class ObserveIsMeetingsEnabledUseCaseImpl(
    private val userConfigRepository: UserConfigRepository,
    private val featureSupport: FeatureSupport,
) : ObserveIsMeetingsEnabledUseCase {
    override suspend operator fun invoke(): Flow<Boolean> = userConfigRepository.observeIsMeetingsEnabled().map { featureFlagEnabled ->
        featureFlagEnabled && featureSupport.isMeetingsSupported
    }
}

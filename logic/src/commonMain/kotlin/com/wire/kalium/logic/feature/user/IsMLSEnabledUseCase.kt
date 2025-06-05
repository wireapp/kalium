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

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.common.functional.fold
import io.mockative.Mockable

/**
 * Checks if the current user has enabled MLS support.
 */
@Mockable
interface IsMLSEnabledUseCase {
    /**
     * @return true if MLS is enabled, false otherwise.
     */
    suspend operator fun invoke(): Boolean
}

internal class IsMLSEnabledUseCaseImpl(
    private val featureSupport: FeatureSupport,
    private val userConfigRepository: UserConfigRepository
) : IsMLSEnabledUseCase {

    override suspend operator fun invoke(): Boolean =
        userConfigRepository.isMLSEnabled().fold({
            false
        }, {
            it && featureSupport.isMLSSupported
        })
}

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
import com.wire.kalium.common.functional.fold

/**
 * Checks if the current user's team has enabled E2EI and MLS.
 */
interface IsE2EIEnabledUseCase {
    /**
     * @return true if E2EI and MLS is enabled, false otherwise.
     */
    suspend operator fun invoke(): Boolean
}

internal class IsE2EIEnabledUseCaseImpl(
    private val userConfigRepository: UserConfigRepository,
    private val isMLSEnabledUseCase: IsMLSEnabledUseCase
) : IsE2EIEnabledUseCase {

    override suspend operator fun invoke(): Boolean =
        userConfigRepository.getE2EISettings().fold({
            false
        }, {
            it.isRequired && isMLSEnabledUseCase()
        })
}

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

package com.wire.kalium.logic.feature.user.guestroomlink

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.onSuccess

/**
 * Mark Guest Link Feature Flag state as not changed
 */
interface MarkGuestLinkFeatureFlagAsNotChangedUseCase {
    suspend operator fun invoke()
}

class MarkGuestLinkFeatureFlagAsNotChangedUseCaseImpl internal constructor(
    private val userConfigRepository: UserConfigRepository
) : MarkGuestLinkFeatureFlagAsNotChangedUseCase {
    override suspend operator fun invoke() {
        userConfigRepository.getGuestRoomLinkStatus().onSuccess {
            it.isStatusChanged?.let { isEnabled ->
                userConfigRepository.setGuestRoomStatus(isEnabled, false)
            }
        }
    }
}

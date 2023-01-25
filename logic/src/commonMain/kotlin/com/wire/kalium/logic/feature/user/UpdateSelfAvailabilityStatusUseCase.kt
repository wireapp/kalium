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

package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserRepository

/**
 * Updates the current user's [UserAvailabilityStatus] status.
 * @see [UserAvailabilityStatus]
 */
class UpdateSelfAvailabilityStatusUseCase internal constructor(
    private val userRepository: UserRepository,
) {
    /**
     * @param status the new [UserAvailabilityStatus] status.
     */
    suspend operator fun invoke(status: UserAvailabilityStatus) {
        // TODO: Handle possibility of being offline. Storing the broadcast to be sent when Sync is done.
        // For now, we don't need Sync, as we do not broadcast the availability to other devices or users.
        userRepository.updateSelfUserAvailabilityStatus(status)
    }
}

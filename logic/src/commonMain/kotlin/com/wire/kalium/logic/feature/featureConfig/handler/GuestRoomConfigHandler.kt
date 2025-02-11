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
package com.wire.kalium.logic.feature.featureConfig.handler

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.ConfigsStatusModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold

class GuestRoomConfigHandler(
    private val userConfigRepository: UserConfigRepository,
    private val kaliumConfigs: KaliumConfigs
) {
    fun handle(guestRoomConfig: ConfigsStatusModel): Either<CoreFailure, Unit> =
        if (!kaliumConfigs.guestRoomLink) {
            userConfigRepository.setGuestRoomStatus(false, null)
        } else {
            val status: Boolean = guestRoomConfig.status == Status.ENABLED
            val hasStatusChanged = userConfigRepository.getGuestRoomLinkStatus().fold(
                {
                    false
                }, {
                    it.isGuestRoomLinkEnabled != status
                }
            )
            userConfigRepository.setGuestRoomStatus(status, hasStatusChanged)
        }
}

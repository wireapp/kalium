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
import com.wire.kalium.logic.configuration.FeatureConfigRepository
import com.wire.kalium.logic.data.featureConfig.ConfigsStatusModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold

public class GuestRoomConfigHandler(
    private val userConfigRepository: FeatureConfigRepository,
    private val isGuestRoomLinkSupported: Boolean
) {
    public suspend fun handle(guestRoomConfig: ConfigsStatusModel): Either<CoreFailure, Unit> =
        if (!isGuestRoomLinkSupported) {
            userConfigRepository.setGuestRoomStatus(false, null)
        } else {
            val status: Boolean = guestRoomConfig.status == Status.ENABLED
            val hasStatusChanged = userConfigRepository.getGuestRoomLinkStatus().fold(
                {
                    false
                },
                {
                    it.isGuestRoomLinkEnabled != status
                }
            )
            userConfigRepository.setGuestRoomStatus(status, hasStatusChanged)
        }
}

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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.ConferenceCallingModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap

class ConferenceCallingConfigHandler(
    private val userConfigRepository: UserConfigRepository
) {
    fun handle(conferenceCallingConfig: ConferenceCallingModel): Either<CoreFailure, Unit> {
        val conferenceCallingEnabled = conferenceCallingConfig.status == Status.ENABLED
        val result = userConfigRepository.setConferenceCallingEnabled(conferenceCallingEnabled).flatMap {
            userConfigRepository.setUseSFTForOneOnOneCalls(conferenceCallingConfig.useSFTForOneOnOneCalls)
        }
        return result
    }
}

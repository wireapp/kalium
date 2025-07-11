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

package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.util.PlatformContext
import kotlinx.coroutines.flow.Flow

actual class MediaManagerServiceImpl(
    platformContext: PlatformContext
) : MediaManagerService {
    actual override suspend fun turnLoudSpeakerOn() {
        TODO("Not yet implemented")
    }

    actual override suspend fun turnLoudSpeakerOff() {
        TODO("Not yet implemented")
    }

    actual override fun observeSpeaker(): Flow<Boolean> {
        TODO("Not yet implemented")
    }

    actual override suspend fun startMediaManager() {
        TODO("Not yet implemented")
    }
}

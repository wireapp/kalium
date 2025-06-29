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

import io.mockative.Mockable
import kotlinx.coroutines.flow.Flow

@Mockable
interface MediaManagerService {
    suspend fun turnLoudSpeakerOn()
    suspend fun turnLoudSpeakerOff()
    fun observeSpeaker(): Flow<Boolean>

    /**
     * Suspends the execution of the current coroutine until the media manager is started.
     * If it is already started, then it returns instantly.
     */
    suspend fun startMediaManager()
}

expect class MediaManagerServiceImpl : MediaManagerService {
    override suspend fun turnLoudSpeakerOn()
    override suspend fun turnLoudSpeakerOff()
    override fun observeSpeaker(): Flow<Boolean>
    override suspend fun startMediaManager()
}

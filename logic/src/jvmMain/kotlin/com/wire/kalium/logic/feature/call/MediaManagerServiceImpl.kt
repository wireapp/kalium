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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

@Suppress("UnusedPrivateProperty")
internal actual open class MediaManagerServiceImpl(
    platformContext: PlatformContext,
    scope: CoroutineScope,
    private val mediaRegistry: JvmCallMediaRegistry = JvmCallMediaProvider.registry,
) : MediaManagerService {
    actual override suspend fun turnLoudSpeakerOn() {
        mediaRegistry.requireMedia().setSpeaker(enabled = true)
    }

    actual override suspend fun turnLoudSpeakerOff() {
        mediaRegistry.requireMedia().setSpeaker(enabled = false)
    }

    actual override fun observeSpeaker(): Flow<Boolean> = mediaRegistry.requireMedia().route
        .filterIsInstance<JvmAudioRouteState.Confirmed>()
        .map { it.speaker }
        .distinctUntilChanged()

    actual override suspend fun startMediaManager() {
        val media = mediaRegistry.requireMedia()
        media.startMediaManager()
    }
}

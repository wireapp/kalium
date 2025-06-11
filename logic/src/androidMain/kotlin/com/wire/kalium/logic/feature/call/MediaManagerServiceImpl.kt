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

import com.waz.media.manager.MediaManager
import com.waz.media.manager.MediaManagerListener
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.util.PlatformContext
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onStart

actual class MediaManagerServiceImpl(
    platformContext: PlatformContext,
    scope: CoroutineScope,
    dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : MediaManagerService {

    private val mediaManager: Deferred<MediaManager> = scope.async(
        start = CoroutineStart.LAZY,
        context = dispatchers.default
    ) {
        MediaManager.getInstance(platformContext.context).apply {
            addListener(object : MediaManagerListener {
                override fun onPlaybackRouteChanged(route: Int) {
                    _isLoudSpeakerOnFlow.value = this@apply.isLoudSpeakerOn
                    kaliumLogger.w("onPlaybackRouteChanged called with route = $route..") // Nothing to do for now
                }

                override fun mediaCategoryChanged(
                    conversationId: String?,
                    category: Int
                ): Int =
                    category
            })
        }
    }

    actual override suspend fun startMediaManager() {
        mediaManager.await()
    }

    private val _isLoudSpeakerOnFlow = MutableStateFlow(false)
    private val isLoudSpeakerOnFlow = _isLoudSpeakerOnFlow.asStateFlow().onStart {
        _isLoudSpeakerOnFlow.value = mediaManager.await().isLoudSpeakerOn
    }

    actual override suspend fun turnLoudSpeakerOn() {
        mediaManager.await().turnLoudSpeakerOn()
    }

    actual override suspend fun turnLoudSpeakerOff() {
        mediaManager.await().turnLoudSpeakerOff()
    }

    actual override fun observeSpeaker(): Flow<Boolean> = isLoudSpeakerOnFlow
}

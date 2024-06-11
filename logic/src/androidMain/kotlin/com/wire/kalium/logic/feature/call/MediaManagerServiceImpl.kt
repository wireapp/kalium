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
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.PlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

actual class MediaManagerServiceImpl(
    platformContext: PlatformContext,
) : MediaManagerService {

    private val mediaManager: MediaManager by lazy {
        runBlocking {
            withContext(Dispatchers.Default) {
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
        }
    }

    private val _isLoudSpeakerOnFlow = MutableStateFlow(mediaManager.isLoudSpeakerOn)
    private val isLoudSpeakerOnFlow = _isLoudSpeakerOnFlow.asStateFlow()

    override fun turnLoudSpeakerOn() {
        mediaManager.turnLoudSpeakerOn()
    }

    override fun turnLoudSpeakerOff() {
        mediaManager.turnLoudSpeakerOff()
    }

    override fun observeSpeaker(): StateFlow<Boolean> = isLoudSpeakerOnFlow
}

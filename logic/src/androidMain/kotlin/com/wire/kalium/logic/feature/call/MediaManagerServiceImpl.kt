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

package com.wire.kalium.logic.feature.call

import android.content.Context
import com.waz.media.manager.MediaManager
import com.waz.media.manager.MediaManagerListener
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class MediaManagerServiceImpl(
    appContext: Context
) : MediaManagerService {

    private val mediaManager: MediaManager = MediaManager.getInstance(appContext).apply {
        addListener(object : MediaManagerListener {
            override fun onPlaybackRouteChanged(route: Int) {
                _isLoudSpeakerOnFlow.value = this@apply.isLoudSpeakerOn
                kaliumLogger.w("onPlaybackRouteChanged called with route = $route..") // Nothing to do for now
            }

            // we don't need to do anything in here, I guess, and the return value gets ignored anyway
            override fun mediaCategoryChanged(conversationId: String?, category: Int): Int = category
        })
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

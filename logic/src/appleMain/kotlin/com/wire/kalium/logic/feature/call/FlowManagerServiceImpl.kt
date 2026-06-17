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

import avs.AVSFlowManager
import avs.AVSMediaManager
import com.wire.kalium.common.logger.callingLogger
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.util.PlatformContext
import com.wire.kalium.logic.util.PlatformRotation
import com.wire.kalium.logic.util.PlatformView

internal actual open class FlowManagerServiceImpl(
    appContext: PlatformContext
) : FlowManagerService {
    private val mediaManager by lazy {
        AVSMediaManager.defaultMediaManager() ?: AVSMediaManager()
    }

    private val flowManager by lazy {
        AVSFlowManager(delegate = null, mediaManager = mediaManager).also {
            it.setEnableLogging(true)
        }
    }

    actual override suspend fun setVideoPreview(conversationId: ConversationId, view: PlatformView) {
        if (!flowManager.attachPlatformVideoView(view)) {
            kaliumLogger.w("AVS iOS: setVideoPreview ignored because view is null")
            return
        }
        callingLogger.i("AVS iOS: attached remote video view for conversation=${conversationId.value}")
    }

    actual override suspend fun flipToFrontCamera(conversationId: ConversationId) {
        flowManager.setVideoCaptureDevice(deviceId = "front", forConversation = conversationId.toString())
        callingLogger.i("AVS iOS: switched to front camera for conversation=${conversationId.value}")
    }

    actual override suspend fun flipToBackCamera(conversationId: ConversationId) {
        flowManager.setVideoCaptureDevice(deviceId = "back", forConversation = conversationId.toString())
        callingLogger.i("AVS iOS: switched to back camera for conversation=${conversationId.value}")
    }

    actual override suspend fun setUIRotation(rotation: PlatformRotation) {
        kaliumLogger.w("AVS iOS: setUIRotation not exposed in current AVSFlowManager API")
    }

    actual override suspend fun startFlowManager() {
        if (!AppleAvsInterop.startIfAvailable()) {
            kaliumLogger.w("AVS iOS smoke: startFlowManager could not start AVS")
            return
        }
        mediaManager.startAudio()
        flowManager
    }
}

internal expect fun AVSFlowManager.attachPlatformVideoView(view: PlatformView): Boolean

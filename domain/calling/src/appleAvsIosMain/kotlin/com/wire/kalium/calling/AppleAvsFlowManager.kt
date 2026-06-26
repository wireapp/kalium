/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.calling

import avs.AVSFlowManager
import avs.AVSMediaManager
import platform.UIKit.UIView

actual class AppleAvsFlowManager actual constructor() {
    private val mediaManager by lazy {
        AVSMediaManager.defaultMediaManager() ?: AVSMediaManager()
    }

    private val flowManager by lazy {
        AVSFlowManager(delegate = null, mediaManager = mediaManager).also {
            it.setEnableLogging(true)
        }
    }

    actual fun attachVideoView(view: Any?): Boolean {
        val videoView = view as? UIView ?: return false
        flowManager.attachVideoView(videoView)
        return true
    }

    actual fun setVideoCaptureDevice(deviceId: String, forConversation: String) {
        flowManager.setVideoCaptureDevice(deviceId = deviceId, forConversation = forConversation)
    }

    actual fun startAudio() {
        mediaManager.startAudio()
        flowManager
    }

    actual fun startIfAvailable(): Boolean = AppleAvs.bridge.startIfAvailable()
}

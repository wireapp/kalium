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
import avs.AVSVideoView
import platform.UIKit.UIView

/** Uses the runtime's AVSFlowManager. A second AVSFlowManager would start a second AVS loop. */
actual class AppleAvsFlowManager actual constructor() {
    private val flowManager get() = AppleAvsPlatformRuntime.flowManager()

    actual fun attachLocalVideoPreview(view: Any?): Boolean = withFlowManager(view as? UIView) { preview ->
        attachVideoPreview(preview)
    }

    actual fun detachLocalVideoPreview(view: Any?): Boolean = withFlowManager(view as? UIView) { preview ->
        detachVideoPreview(preview)
    }

    actual fun startLocalVideoCapture() {
        flowManager?.startVideoCapture()
    }

    actual fun stopLocalVideoCapture() {
        flowManager?.stopVideoCapture()
    }

    actual fun attachRemoteVideoView(view: Any?, userId: String, clientId: String): Boolean =
        withFlowManager(view as? AVSVideoView) { remoteView ->
            remoteView.userid = userId
            remoteView.clientid = clientId
            attachVideoView(remoteView)
        }

    actual fun detachRemoteVideoView(view: Any?): Boolean = withFlowManager(view as? AVSVideoView) { remoteView ->
        detachVideoView(remoteView)
    }

    actual fun setVideoCaptureDevice(deviceId: String, forConversation: String) {
        flowManager?.setVideoCaptureDevice(deviceId = deviceId, forConversation = forConversation)
    }

    actual fun startIfAvailable(): Boolean = AppleAvs.bridge.startIfAvailable()

    private inline fun <T : Any> withFlowManager(view: T?, action: AVSFlowManager.(T) -> Unit): Boolean {
        val flowManager = flowManager
        if (view == null || flowManager == null) return false
        flowManager.action(view)
        return true
    }
}

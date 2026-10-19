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

package com.wire.kalium.logic.feature.call

import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceDiscoverySession
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDevicePositionUnspecified
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.position

internal enum class AppleCameraPosition { FRONT, BACK }

internal data class AppleCameraDevice(
    val id: String,
    val position: AppleCameraPosition,
)

internal class AppleCameraController(
    private val devices: () -> List<AppleCameraDevice> = ::discoverAppleCameras,
    private val setCaptureDevice: (deviceId: String, conversationId: String) -> Unit,
) {
    fun select(position: AppleCameraPosition, conversationId: String): Boolean {
        val deviceId = devices().firstOrNull { it.position == position }?.id ?: return false
        setCaptureDevice(deviceId, conversationId)
        return true
    }
}

private fun discoverAppleCameras(): List<AppleCameraDevice> =
    AVCaptureDeviceDiscoverySession.discoverySessionWithDeviceTypes(
        deviceTypes = listOf(AVCaptureDeviceTypeBuiltInWideAngleCamera),
        mediaType = AVMediaTypeVideo,
        position = AVCaptureDevicePositionUnspecified,
    ).devices.mapNotNull { device ->
        val captureDevice = device as AVCaptureDevice
        val position = when (captureDevice.position()) {
            AVCaptureDevicePositionFront -> AppleCameraPosition.FRONT
            AVCaptureDevicePositionBack -> AppleCameraPosition.BACK
            else -> null
        }
        position?.let { AppleCameraDevice(captureDevice.uniqueID, it) }
    }

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

import kotlinx.cinterop.COpaquePointer

actual object AppleAvs {
    actual val bridge: AppleAvsBridge = NoOpAppleAvsBridge
}

actual class AppleAvsFlowManager actual constructor() {
    actual fun attachVideoView(view: Any?): Boolean = false
    actual fun setVideoCaptureDevice(deviceId: String, forConversation: String) = Unit
    actual fun startAudio() = Unit
    actual fun startIfAvailable(): Boolean = false
}

@Suppress("TooManyFunctions")
private object NoOpAppleAvsBridge : AppleAvsBridge {
    override fun startIfAvailable(): Boolean = false
    override fun userHandle(selfUserId: String, selfClientId: String, callbacks: AppleAvsCallbacks): UInt? = null

    override fun receiveCallingMessage(
        handle: UInt,
        payload: ByteArray,
        currentTimeSeconds: UInt,
        messageTimeSeconds: UInt,
        conversationId: String,
        senderUserId: String,
        senderClientId: String,
        conversationType: Int
    ): Boolean = false

    override fun respondToSend(handle: UInt, status: Int, reason: String, context: COpaquePointer?) = Unit
    override fun respondToSft(handle: UInt, error: Int, data: ByteArray, context: COpaquePointer?) = Unit
    override fun updateConfig(handle: UInt, error: Int, json: String) = Unit
    override fun startCall(handle: UInt, conversationId: String, callType: Int, conversationType: Int, audioCbr: Boolean): Int = 0
    override fun answerCall(handle: UInt, conversationId: String, callType: Int, audioCbr: Boolean): Int = 0
    override fun endCall(handle: UInt, conversationId: String) = Unit
    override fun rejectCall(handle: UInt, conversationId: String): Int = 0
    override fun setMute(handle: UInt, muted: Boolean) = Unit
    override fun setVideoSendState(handle: UInt, conversationId: String, state: Int) = Unit
    override fun requestVideoStreams(handle: UInt, conversationId: String, mode: Int, json: String): Int = 0

    override fun setEpochInfo(
        handle: UInt,
        conversationId: String,
        epoch: UInt,
        clientsJson: String,
        keyBase64: String
    ): Int = 0

    override fun setClientsForConversation(handle: UInt, conversationId: String, clients: String): Int = 0
    override fun processNotifications(handle: UInt, isStarted: Boolean): Int = 0
    override fun setBackground(handle: UInt, background: Boolean): Int = 0

    override fun setNetworkQualityInterval(
        handle: UInt,
        callbacks: AppleAvsCallbacks,
        intervalInSeconds: Int
    ) = Unit

    override fun notifyNetworkChangedIfAvailable(): Boolean = false
}

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

@file:Suppress("LongParameterList", "TooManyFunctions")

package com.wire.kalium.calling

import kotlinx.cinterop.COpaquePointer

@Suppress("TooManyFunctions")
interface AppleAvsBridge {
    fun startIfAvailable(): Boolean
    fun userHandle(selfUserId: String, selfClientId: String, callbacks: AppleAvsCallbacks): UInt?
    fun receiveCallingMessage(
        handle: UInt,
        payload: ByteArray,
        currentTimeSeconds: UInt,
        messageTimeSeconds: UInt,
        conversationId: String,
        senderUserId: String,
        senderClientId: String,
        conversationType: Int
    ): Boolean
    fun respondToSend(handle: UInt, status: Int, reason: String, context: COpaquePointer?)
    fun respondToSft(handle: UInt, error: Int, data: ByteArray, context: COpaquePointer?)
    fun updateConfig(handle: UInt, error: Int, json: String)
    fun startCall(handle: UInt, conversationId: String, callType: Int, conversationType: Int, audioCbr: Boolean): Int
    fun answerCall(handle: UInt, conversationId: String, callType: Int, audioCbr: Boolean): Int
    fun endCall(handle: UInt, conversationId: String)
    fun rejectCall(handle: UInt, conversationId: String): Int
    fun setMute(handle: UInt, muted: Boolean)
    fun setVideoSendState(handle: UInt, conversationId: String, state: Int)
    fun requestVideoStreams(handle: UInt, conversationId: String, mode: Int, json: String): Int
    fun setEpochInfo(handle: UInt, conversationId: String, epoch: UInt, clientsJson: String, keyBase64: String): Int
    fun setClientsForConversation(handle: UInt, conversationId: String, clients: String): Int
    fun processNotifications(handle: UInt, isStarted: Boolean): Int
    fun setBackground(handle: UInt, background: Boolean): Int
    fun setNetworkQualityInterval(handle: UInt, callbacks: AppleAvsCallbacks, intervalInSeconds: Int)
    fun notifyNetworkChangedIfAvailable(): Boolean
}

interface AppleAvsCallbacks {
    fun onReady(version: Int)
    fun onSend(
        context: COpaquePointer?,
        conversationId: String?,
        selfUserId: String?,
        selfClientId: String?,
        targetRecipientsJson: String?,
        clientIdDestination: String?,
        data: ByteArray,
        transient: Boolean,
        myClientsOnly: Boolean
    ): Int
    fun onSftRequest(context: COpaquePointer?, url: String?, data: ByteArray): Int
    fun onIncomingCall(
        conversationId: String?,
        messageTime: UInt,
        userId: String?,
        clientId: String?,
        video: Boolean,
        shouldRing: Boolean,
        conversationType: Int
    )
    fun onMissedCall(conversationId: String?, messageTime: UInt, userId: String?, video: Boolean)
    fun onAnsweredCall(conversationId: String?)
    fun onEstablishedCall(conversationId: String?, userId: String?, clientId: String?)
    fun onClosedCall(reason: Int, conversationId: String?, messageTime: UInt, userId: String?, clientId: String?)
    fun onMetrics(conversationId: String?, metricsJson: String?)
    fun onConfigRequest(handle: UInt, context: COpaquePointer?): Int
    fun onAudioCbrChanged(userId: String?, clientId: String?, enabled: Boolean)
    fun onVideoStateChanged(conversationId: String?, userId: String?, clientId: String?, state: Int)
    fun onParticipantChanged(conversationId: String?, data: String?)
    fun onNetworkQualityChanged(conversationId: String?, userId: String?, clientId: String?, qualityInfoJson: String?)
    fun onRequestNewEpoch(handle: UInt, conversationId: String?)
    fun onClientsRequest(handle: UInt, conversationId: String?)
    fun onActiveSpeakersChanged(handle: UInt, conversationId: String?, data: String?)
    fun onMuteStateChanged(isMuted: Boolean)
}

expect object AppleAvs {
    val bridge: AppleAvsBridge
}

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

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.wire.kalium.calling

import avs.wcall_answer
import avs.wcall_config_update
import avs.wcall_create
import avs.wcall_end
import avs.wcall_network_changed
import avs.wcall_process_notifications
import avs.wcall_recv_msg
import avs.wcall_reject
import avs.wcall_request_video_streams
import avs.wcall_resp
import avs.wcall_run
import avs.wcall_set_active_speaker_handler
import avs.wcall_set_background
import avs.wcall_set_clients_for_conv
import avs.wcall_set_epoch_info
import avs.wcall_set_group_changed_handler
import avs.wcall_set_mute
import avs.wcall_set_mute_handler
import avs.wcall_set_network_quality_handler
import avs.wcall_set_participant_changed_handler
import avs.wcall_set_req_clients_handler
import avs.wcall_set_req_new_epoch_handler
import avs.wcall_set_video_send_state
import avs.wcall_sft_resp
import avs.wcall_setup
import avs.wcall_start
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned

actual object AppleAvs {
    actual val bridge: AppleAvsBridge = AppleAvsBridgeImpl
}

@Suppress("LongParameterList", "ReturnCount", "TooManyFunctions")
private object AppleAvsBridgeImpl : AppleAvsBridge {
    data class CreatedHandle(val handle: UInt, val stableRef: StableRef<AppleAvsCallbacks>)

    private var isStarted = false
    private val handles = mutableMapOf<String, CreatedHandle>()

    private fun callbacks(arg: COpaquePointer?): AppleAvsCallbacks? = arg?.asStableRef<AppleAvsCallbacks>()?.get()
    private fun CPointer<ByteVar>?.string(): String? = this?.toKString()
    private fun CPointer<UByteVar>?.bytes(length: ULong): ByteArray = this?.readBytes(length.toInt()) ?: byteArrayOf()

    private val readyHandler = staticCFunction { version: Int, arg: COpaquePointer? ->
        callbacks(arg)?.onReady(version)
        Unit
    }

    private val sendHandler = staticCFunction {
            context: COpaquePointer?,
            conversationId: CPointer<ByteVar>?,
            userId: CPointer<ByteVar>?,
            clientId: CPointer<ByteVar>?,
            targetRecipientsJson: CPointer<ByteVar>?,
            clientIdDestination: CPointer<ByteVar>?,
            data: CPointer<UByteVar>?,
            length: ULong,
            transient: Int,
            myClientsOnly: Int,
            arg: COpaquePointer? ->
        callbacks(arg)?.onSend(
            context = context,
            conversationId = conversationId.string(),
            selfUserId = userId.string(),
            selfClientId = clientId.string(),
            targetRecipientsJson = targetRecipientsJson.string(),
            clientIdDestination = clientIdDestination.string(),
            data = data.bytes(length),
            transient = transient != 0,
            myClientsOnly = myClientsOnly != 0
        ) ?: INVALID_ARGUMENT_ERROR
    }

    private val sftRequestHandler = staticCFunction {
            context: COpaquePointer?,
            url: CPointer<ByteVar>?,
            data: CPointer<UByteVar>?,
            length: ULong,
            arg: COpaquePointer? ->
        callbacks(arg)?.onSftRequest(context, url.string(), data.bytes(length)) ?: INVALID_ARGUMENT_ERROR
    }

    private val incomingHandler = staticCFunction {
            conversationId: CPointer<ByteVar>?,
            msgTime: UInt,
            userId: CPointer<ByteVar>?,
            clientId: CPointer<ByteVar>?,
            video: Int,
            shouldRing: Int,
            conversationType: Int,
            arg: COpaquePointer? ->
        callbacks(arg)?.onIncomingCall(
            conversationId.string(),
            msgTime,
            userId.string(),
            clientId.string(),
            video != 0,
            shouldRing != 0,
            conversationType
        )
        Unit
    }

    private val missedHandler = staticCFunction {
            conversationId: CPointer<ByteVar>?,
            msgTime: UInt,
            userId: CPointer<ByteVar>?,
            _: CPointer<ByteVar>?,
            video: Int,
            arg: COpaquePointer? ->
        callbacks(arg)?.onMissedCall(conversationId.string(), msgTime, userId.string(), video != 0)
        Unit
    }

    private val answeredHandler = staticCFunction { conversationId: CPointer<ByteVar>?, arg: COpaquePointer? ->
        callbacks(arg)?.onAnsweredCall(conversationId.string())
        Unit
    }

    private val establishedHandler = staticCFunction {
            conversationId: CPointer<ByteVar>?,
            userId: CPointer<ByteVar>?,
            clientId: CPointer<ByteVar>?,
            arg: COpaquePointer? ->
        callbacks(arg)?.onEstablishedCall(conversationId.string(), userId.string(), clientId.string())
        Unit
    }

    private val closeHandler = staticCFunction {
            reason: Int,
            conversationId: CPointer<ByteVar>?,
            msgTime: UInt,
            userId: CPointer<ByteVar>?,
            clientId: CPointer<ByteVar>?,
            arg: COpaquePointer? ->
        callbacks(arg)?.onClosedCall(reason, conversationId.string(), msgTime, userId.string(), clientId.string())
        Unit
    }

    private val metricsHandler = staticCFunction {
            conversationId: CPointer<ByteVar>?,
            metricsJson: CPointer<ByteVar>?,
            arg: COpaquePointer? ->
        callbacks(arg)?.onMetrics(conversationId.string(), metricsJson.string())
        Unit
    }

    private val configRequestHandler = staticCFunction { handle: UInt, arg: COpaquePointer? ->
        callbacks(arg)?.onConfigRequest(handle, arg) ?: INVALID_ARGUMENT_ERROR
    }

    private val audioCbrHandler = staticCFunction {
            userId: CPointer<ByteVar>?,
            clientId: CPointer<ByteVar>?,
            enabled: Int,
            arg: COpaquePointer? ->
        callbacks(arg)?.onAudioCbrChanged(userId.string(), clientId.string(), enabled != 0)
        Unit
    }

    private val videoStateHandler = staticCFunction {
            conversationId: CPointer<ByteVar>?,
            userId: CPointer<ByteVar>?,
            clientId: CPointer<ByteVar>?,
            state: Int,
            arg: COpaquePointer? ->
        callbacks(arg)?.onVideoStateChanged(conversationId.string(), userId.string(), clientId.string(), state)
        Unit
    }

    private val participantChangedHandler = staticCFunction {
            conversationId: CPointer<ByteVar>?,
            data: CPointer<ByteVar>?,
            arg: COpaquePointer? ->
        callbacks(arg)?.onParticipantChanged(conversationId.string(), data.string())
        Unit
    }

    private val networkQualityHandler = staticCFunction {
            conversationId: CPointer<ByteVar>?,
            userId: CPointer<ByteVar>?,
            clientId: CPointer<ByteVar>?,
            qualityInfoJson: CPointer<ByteVar>?,
            arg: COpaquePointer? ->
        callbacks(arg)?.onNetworkQualityChanged(conversationId.string(), userId.string(), clientId.string(), qualityInfoJson.string())
        Unit
    }

    private val requestNewEpochHandler = staticCFunction { handle: UInt, conversationId: CPointer<ByteVar>?, arg: COpaquePointer? ->
        callbacks(arg)?.onRequestNewEpoch(handle, conversationId.string())
        Unit
    }

    private val clientsRequestHandler = staticCFunction { handle: UInt, conversationId: CPointer<ByteVar>?, arg: COpaquePointer? ->
        callbacks(arg)?.onClientsRequest(handle, conversationId.string())
        Unit
    }

    private val activeSpeakerHandler = staticCFunction {
            handle: UInt,
            conversationId: CPointer<ByteVar>?,
            data: CPointer<ByteVar>?,
            arg: COpaquePointer? ->
        callbacks(arg)?.onActiveSpeakersChanged(handle, conversationId.string(), data.string())
        Unit
    }

    private val muteHandler = staticCFunction { isMuted: Int, arg: COpaquePointer? ->
        callbacks(arg)?.onMuteStateChanged(isMuted != 0)
        Unit
    }

    override fun startIfAvailable(): Boolean {
        if (isStarted) return true

        return runCatching {
            wcall_setup()
            wcall_run()
            isStarted = true
            true
        }.getOrElse {
            false
        }
    }

    override fun userHandle(selfUserId: String, selfClientId: String, callbacks: AppleAvsCallbacks): UInt? {
        if (!startIfAvailable()) return null

        val key = "$selfUserId:$selfClientId"
        handles[key]?.let { return it.handle }

        return runCatching {
            val stableRef = StableRef.create(callbacks)
            val arg = stableRef.asCPointer()
            val handle = wcall_create(
                userid = selfUserId,
                clientid = selfClientId,
                readyh = readyHandler,
                sendh = sendHandler,
                sfth = sftRequestHandler,
                incomingh = incomingHandler,
                missedh = missedHandler,
                answerh = answeredHandler,
                estabh = establishedHandler,
                closeh = closeHandler,
                metricsh = metricsHandler,
                cfg_reqh = configRequestHandler,
                acbrh = audioCbrHandler,
                vstateh = videoStateHandler,
                arg = arg
            )
            registerAdditionalHandlers(handle, arg)
            handles[key] = CreatedHandle(handle, stableRef)
            handle
        }.getOrElse {
            null
        }
    }

    private fun registerAdditionalHandlers(handle: UInt, arg: COpaquePointer) {
        wcall_set_participant_changed_handler(handle, participantChangedHandler, arg)
        wcall_set_network_quality_handler(handle, networkQualityHandler, DEFAULT_NETWORK_QUALITY_INTERVAL_SECONDS, arg)
        wcall_set_req_new_epoch_handler(handle, requestNewEpochHandler)
        wcall_set_req_clients_handler(handle, clientsRequestHandler)
        wcall_set_active_speaker_handler(handle, activeSpeakerHandler)
        wcall_set_mute_handler(handle, muteHandler, arg)
        wcall_set_group_changed_handler(handle, null, arg)
    }

    override fun receiveCallingMessage(
        handle: UInt,
        payload: ByteArray,
        currentTimeSeconds: UInt,
        messageTimeSeconds: UInt,
        conversationId: String,
        senderUserId: String,
        senderClientId: String,
        conversationType: Int
    ): Boolean {
        if (!startIfAvailable()) return false
        if (payload.isEmpty()) return false

        return runCatching {
            payload.usePinned { pinned ->
                wcall_recv_msg(
                    wuser = handle,
                    buf = pinned.addressOf(0).reinterpret(),
                    len = payload.size.toULong(),
                    curr_time = currentTimeSeconds,
                    msg_time = messageTimeSeconds,
                    convid = conversationId,
                    userid = senderUserId,
                    clientid = senderClientId,
                    conv_type = conversationType,
                    meeting = 0
                )
            }
            true
        }.getOrElse {
            false
        }
    }

    override fun respondToSend(handle: UInt, status: Int, reason: String, context: COpaquePointer?) {
        if (!startIfAvailable()) return
        wcall_resp(handle, status, reason, context)
    }

    override fun respondToSft(handle: UInt, error: Int, data: ByteArray, context: COpaquePointer?) {
        if (!startIfAvailable()) return
        data.usePinned { pinned ->
            wcall_sft_resp(handle, error, pinned.addressOf(0).reinterpret(), data.size.toULong(), context)
        }
    }

    override fun updateConfig(handle: UInt, error: Int, json: String) {
        if (!startIfAvailable()) return
        wcall_config_update(handle, error, json)
    }

    override fun startCall(handle: UInt, conversationId: String, callType: Int, conversationType: Int, audioCbr: Boolean): Int =
        if (startIfAvailable()) wcall_start(handle, conversationId, callType, conversationType, audioCbr.toAvsInt(), 0) else -1

    override fun answerCall(handle: UInt, conversationId: String, callType: Int, audioCbr: Boolean): Int =
        if (startIfAvailable()) wcall_answer(handle, conversationId, callType, audioCbr.toAvsInt()) else -1

    override fun endCall(handle: UInt, conversationId: String) {
        if (!startIfAvailable()) return
        wcall_end(handle, conversationId)
    }

    override fun rejectCall(handle: UInt, conversationId: String): Int =
        if (startIfAvailable()) wcall_reject(handle, conversationId) else -1

    override fun setMute(handle: UInt, muted: Boolean) {
        if (!startIfAvailable()) return
        wcall_set_mute(handle, muted.toAvsInt())
    }

    override fun setVideoSendState(handle: UInt, conversationId: String, state: Int) {
        if (!startIfAvailable()) return
        wcall_set_video_send_state(handle, conversationId, state)
    }

    override fun requestVideoStreams(handle: UInt, conversationId: String, mode: Int, json: String): Int =
        if (startIfAvailable()) wcall_request_video_streams(handle, conversationId, mode, json) else -1

    override fun setEpochInfo(handle: UInt, conversationId: String, epoch: UInt, clientsJson: String, keyBase64: String): Int =
        if (startIfAvailable()) wcall_set_epoch_info(handle, conversationId, epoch, clientsJson, keyBase64) else -1

    override fun setClientsForConversation(handle: UInt, conversationId: String, clients: String): Int =
        if (startIfAvailable()) wcall_set_clients_for_conv(handle, conversationId, clients) else -1

    override fun processNotifications(handle: UInt, isStarted: Boolean): Int =
        if (startIfAvailable()) wcall_process_notifications(handle, isStarted.toAvsInt()) else -1

    override fun setBackground(handle: UInt, background: Boolean): Int =
        if (startIfAvailable()) wcall_set_background(handle, background.toAvsInt()) else -1

    override fun setNetworkQualityInterval(handle: UInt, callbacks: AppleAvsCallbacks, intervalInSeconds: Int) {
        if (!startIfAvailable()) return
        val arg = handles.values.firstOrNull { it.stableRef.get() === callbacks }?.stableRef?.asCPointer()
        wcall_set_network_quality_handler(handle, networkQualityHandler, intervalInSeconds, arg)
    }

    override fun notifyNetworkChangedIfAvailable(): Boolean {
        if (!startIfAvailable()) return false

        return runCatching {
            wcall_network_changed()
            true
        }.getOrElse {
            false
        }
    }

    private fun Boolean.toAvsInt() = if (this) 1 else 0

    private const val DEFAULT_NETWORK_QUALITY_INTERVAL_SECONDS = 1
    private const val INVALID_ARGUMENT_ERROR = 1
}

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

package com.wire.kalium.logic.feature.call

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
import com.wire.kalium.common.logger.kaliumLogger
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

@Suppress("TooManyFunctions")
internal object AppleAvsInterop {
    interface Callbacks {
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
        fun onIncomingCall(conversationId: String?, messageTime: UInt, userId: String?, clientId: String?, video: Boolean, shouldRing: Boolean, conversationType: Int)
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

    data class CreatedHandle(val handle: UInt, val stableRef: StableRef<Callbacks>)

    private var isStarted = false
    private val handles = mutableMapOf<String, CreatedHandle>()

    private fun callbacks(arg: COpaquePointer?): Callbacks? = arg?.asStableRef<Callbacks>()?.get()
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
        ) ?: AvsCallBackError.INVALID_ARGUMENT.value
    }

    private val sftRequestHandler = staticCFunction {
            context: COpaquePointer?,
            url: CPointer<ByteVar>?,
            data: CPointer<UByteVar>?,
            length: ULong,
            arg: COpaquePointer? ->
        callbacks(arg)?.onSftRequest(context, url.string(), data.bytes(length)) ?: AvsCallBackError.INVALID_ARGUMENT.value
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
        callbacks(arg)?.onIncomingCall(conversationId.string(), msgTime, userId.string(), clientId.string(), video != 0, shouldRing != 0, conversationType)
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
        callbacks(arg)?.onConfigRequest(handle, arg) ?: AvsCallBackError.INVALID_ARGUMENT.value
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

    fun startIfAvailable(): Boolean {
        if (isStarted) return true

        return runCatching {
            val setupResult = wcall_setup()
            val runResult = wcall_run()
            isStarted = true
            kaliumLogger.i("AVS iOS smoke: started AVS via cinterop (wcall_setup=$setupResult, wcall_run=$runResult)")
            true
        }.getOrElse { error ->
            kaliumLogger.w("AVS iOS smoke: failed to start AVS via cinterop (${error.message ?: error::class.simpleName})")
            false
        }
    }

    fun userHandle(selfUserId: String, selfClientId: String, callbacks: Callbacks): UInt? {
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
            kaliumLogger.i("AVS iOS smoke: created wcall user handle=$handle for user=$selfUserId client=$selfClientId")
            handle
        }.getOrElse { error ->
            kaliumLogger.w("AVS iOS smoke: failed to create wcall user (${error.message ?: error::class.simpleName})")
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

    fun receiveCallingMessage(
        handle: UInt,
        payload: ByteArray,
        currentTimeSeconds: UInt,
        messageTimeSeconds: UInt,
        conversationId: String,
        senderUserId: String,
        senderClientId: String,
        conversationType: Int
    ): Boolean {
        if (payload.isEmpty()) return false

        return runCatching {
            val result = payload.usePinned { pinned ->
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
            kaliumLogger.i("AVS iOS smoke: wcall_recv_msg result=$result conversation=$conversationId")
            true
        }.getOrElse { error ->
            kaliumLogger.w("AVS iOS smoke: wcall_recv_msg failed (${error.message ?: error::class.simpleName})")
            false
        }
    }

    fun respondToSend(handle: UInt, status: Int, reason: String, context: COpaquePointer?) {
        wcall_resp(handle, status, reason, context)
    }

    fun respondToSft(handle: UInt, error: Int, data: ByteArray, context: COpaquePointer?) {
        data.usePinned { pinned ->
            wcall_sft_resp(handle, error, pinned.addressOf(0).reinterpret(), data.size.toULong(), context)
        }
    }

    fun updateConfig(handle: UInt, error: Int, json: String) {
        wcall_config_update(handle, error, json)
    }

    fun startCall(handle: UInt, conversationId: String, callType: Int, conversationType: Int, audioCbr: Boolean): Int =
        wcall_start(handle, conversationId, callType, conversationType, audioCbr.toAvsInt(), 0)

    fun answerCall(handle: UInt, conversationId: String, callType: Int, audioCbr: Boolean): Int =
        wcall_answer(handle, conversationId, callType, audioCbr.toAvsInt())

    fun endCall(handle: UInt, conversationId: String) = wcall_end(handle, conversationId)

    fun rejectCall(handle: UInt, conversationId: String): Int = wcall_reject(handle, conversationId)

    fun setMute(handle: UInt, muted: Boolean) = wcall_set_mute(handle, muted.toAvsInt())

    fun setVideoSendState(handle: UInt, conversationId: String, state: Int) = wcall_set_video_send_state(handle, conversationId, state)

    fun requestVideoStreams(handle: UInt, conversationId: String, mode: Int, json: String): Int =
        wcall_request_video_streams(handle, conversationId, mode, json)

    fun setEpochInfo(handle: UInt, conversationId: String, epoch: UInt, clientsJson: String, keyBase64: String): Int =
        wcall_set_epoch_info(handle, conversationId, epoch, clientsJson, keyBase64)

    fun setClientsForConversation(handle: UInt, conversationId: String, clients: String): Int =
        wcall_set_clients_for_conv(handle, conversationId, clients)

    fun processNotifications(handle: UInt, isStarted: Boolean): Int =
        wcall_process_notifications(handle, isStarted.toAvsInt())

    fun setBackground(handle: UInt, background: Boolean): Int = wcall_set_background(handle, background.toAvsInt())

    fun setNetworkQualityInterval(handle: UInt, callbacks: Callbacks, intervalInSeconds: Int) {
        val arg = handles.values.firstOrNull { it.stableRef.get() === callbacks }?.stableRef?.asCPointer()
        wcall_set_network_quality_handler(handle, networkQualityHandler, intervalInSeconds, arg)
    }

    fun notifyNetworkChangedIfAvailable(): Boolean {
        if (!startIfAvailable()) return false

        return runCatching {
            wcall_network_changed()
            kaliumLogger.i("AVS iOS smoke: networkChanged propagated to AVS via cinterop")
            true
        }.getOrElse { error ->
            kaliumLogger.w("AVS iOS smoke: failed to propagate networkChanged (${error.message ?: error::class.simpleName})")
            false
        }
    }

    private fun Boolean.toAvsInt() = if (this) 1 else 0

    private const val DEFAULT_NETWORK_QUALITY_INTERVAL_SECONDS = 1
}

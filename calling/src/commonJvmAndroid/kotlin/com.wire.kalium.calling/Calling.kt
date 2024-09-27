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

package com.wire.kalium.calling

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.wire.kalium.calling.callbacks.ActiveSpeakersHandler
import com.wire.kalium.calling.callbacks.AnsweredCallHandler
import com.wire.kalium.calling.callbacks.CallConfigRequestHandler
import com.wire.kalium.calling.callbacks.ClientsRequestHandler
import com.wire.kalium.calling.callbacks.CloseCallHandler
import com.wire.kalium.calling.callbacks.ConstantBitRateStateChangeHandler
import com.wire.kalium.calling.callbacks.EstablishedCallHandler
import com.wire.kalium.calling.callbacks.IncomingCallHandler
import com.wire.kalium.calling.callbacks.LogHandler
import com.wire.kalium.calling.callbacks.MetricsHandler
import com.wire.kalium.calling.callbacks.MissedCallHandler
import com.wire.kalium.calling.callbacks.NetworkQualityChangedHandler
import com.wire.kalium.calling.callbacks.ParticipantChangedHandler
import com.wire.kalium.calling.callbacks.ReadyHandler
import com.wire.kalium.calling.callbacks.RequestNewEpochHandler
import com.wire.kalium.calling.callbacks.SFTRequestHandler
import com.wire.kalium.calling.callbacks.SelfUserMuteHandler
import com.wire.kalium.calling.callbacks.SendHandler
import com.wire.kalium.calling.callbacks.VideoReceiveStateHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.calling.types.Uint32_t

// A magic number used to initialize AVS (required for all mobile platforms).
const val ENVIRONMENT_DEFAULT = 0

@Suppress("FunctionNaming")
interface Calling : Library {

    fun wcall_create(
        userId: String,
        clientId: String,
        readyHandler: ReadyHandler,
        sendHandler: SendHandler,
        sftRequestHandler: SFTRequestHandler,
        incomingCallHandler: IncomingCallHandler,
        missedCallHandler: MissedCallHandler,
        answeredCallHandler: AnsweredCallHandler,
        establishedCallHandler: EstablishedCallHandler,
        closeCallHandler: CloseCallHandler,
        metricsHandler: MetricsHandler,
        callConfigRequestHandler: CallConfigRequestHandler,
        constantBitRateStateChangeHandler: ConstantBitRateStateChangeHandler,
        videoReceiveStateHandler: VideoReceiveStateHandler,
        arg: Pointer?
    ): Handle

    fun wcall_setup()

    fun wcall_setup_ex(flags: Int)

    fun wcall_run()

    fun wcall_start(inst: Handle, conversationId: String, callType: Int, convType: Int, audioCbr: Int): Int

    fun wcall_answer(inst: Handle, conversationId: String, callType: Int, cbrEnabled: Boolean)

    fun wcall_reject(inst: Handle, conversationId: String)

    fun wcall_config_update(inst: Handle, error: Int, jsonString: String)

    fun wcall_library_version(): String

    fun wcall_init(env: Int): Int

    fun wcall_set_log_handler(logHandler: LogHandler, arg: Pointer?)

    fun wcall_end(inst: Handle, conversationId: String)

    fun wcall_set_mute(inst: Handle, muted: Int)

    fun wcall_sft_resp(
        inst: Handle,
        error: Int,
        data: ByteArray,
        length: Int,
        ctx: Pointer?
    )

    @Suppress("LongParameterList")
    fun wcall_recv_msg(
        inst: Handle,
        msg: ByteArray,
        len: Int,
        curr_time: Uint32_t,
        msg_time: Uint32_t,
        convId: String,
        userId: String,
        clientId: String,
        convType: Int
    ): Int

    fun wcall_resp(
        inst: Handle,
        status: Int,
        reason: String,
        arg: Pointer?
    ): Int

    @Suppress("FunctionNaming")
    fun wcall_request_video_streams(
        inst: Handle,
        conversationId: String,
        mode: Int,
        json: String
    )

    fun wcall_set_participant_changed_handler(
        inst: Handle,
        wcall_participant_changed_h: ParticipantChangedHandler,
        arg: Pointer?
    )

    @Suppress("FunctionNaming", "FunctionParameterNaming")
    fun wcall_set_network_quality_handler(
        inst: Handle,
        wcall_network_quality_h: NetworkQualityChangedHandler,
        intervalInSeconds: Int,
        arg: Pointer?
    )

    @Suppress("FunctionNaming")
    fun wcall_set_video_send_state(inst: Handle, conversationId: String, state: Int)

    @Suppress("FunctionNaming", "FunctionParameterNaming")
    fun wcall_set_req_clients_handler(
        inst: Handle,
        wcall_req_clients_h: ClientsRequestHandler
    )

    @Suppress("FunctionNaming")
    fun wcall_set_clients_for_conv(
        inst: Handle,
        convId: String,
        clientsJson: String
    )

    @Suppress("FunctionNaming")
    fun wcall_set_active_speaker_handler(
        inst: Handle,
        activeSpeakersHandler: ActiveSpeakersHandler
    )

    @Suppress("FunctionNaming", "LongParameterList")
    fun wcall_set_epoch_info(
        inst: Handle,
        conversationId: String,
        epoch: Uint32_t,
        clientsJson: String,
        keyData: String,
    ): Int

    @Suppress("FunctionNaming")
    fun wcall_set_req_new_epoch_handler(
        inst: Handle,
        requestNewEpochHandler: RequestNewEpochHandler
    )

    @Suppress("FunctionNaming")
    fun wcall_set_mute_handler(
        inst: Handle,
        selfUserMuteHandler: SelfUserMuteHandler,
        arg: Pointer?
    )

    @Suppress("FunctionNaming")
    fun wcall_process_notifications(
        inst: Handle,
        isStarted: Boolean,
    )

    fun kcall_init(env: Int)
    fun kcall_close()

    fun kcall_set_local_user(
        userid: String,
        clientid: String
    )

    fun kcall_set_wuser(
        inst: Handle
    )

    fun kcall_preview_start()
    fun kcall_preview_stop()

    fun kcall_set_user_vidstate(
        convid: String,
        userid: String,
        clientid: String,
        state: Int
    )

    companion object {
        val INSTANCE: Calling? by lazy {
            try {
                Native.load("avs", Calling::class.java)
            } catch (e: UnsatisfiedLinkError) {
                println("Failed to load calling library: ${e.message}")
                null
            }
        }
    }
}

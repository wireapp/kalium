package com.wire.kalium.calling

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.wire.kalium.calling.callbacks.AnsweredCallHandler
import com.wire.kalium.calling.callbacks.CallConfigRequestHandler
import com.wire.kalium.calling.callbacks.CloseCallHandler
import com.wire.kalium.calling.callbacks.ConstantBitRateStateChangeHandler
import com.wire.kalium.calling.callbacks.EstablishedCallHandler
import com.wire.kalium.calling.callbacks.IncomingCallHandler
import com.wire.kalium.calling.callbacks.LogHandler
import com.wire.kalium.calling.callbacks.MetricsHandler
import com.wire.kalium.calling.callbacks.MissedCallHandler
import com.wire.kalium.calling.callbacks.ParticipantChangedHandler
import com.wire.kalium.calling.callbacks.ReadyHandler
import com.wire.kalium.calling.callbacks.SFTRequestHandler
import com.wire.kalium.calling.callbacks.SendHandler
import com.wire.kalium.calling.callbacks.VideoReceiveStateHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.calling.types.Uint32_t

// A magic number used to initialize AVS (required for all mobile platforms).
const val ENVIRONMENT_DEFAULT = 0

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

    fun wcall_start(inst: Handle, conversationId: String, callType: Int, convType: Int, audioCbr: Int): Int

    fun wcall_answer(inst: Handle, conversationId: String, callType: Int, cbrEnabled: Boolean)

    fun wcall_reject(inst: Handle, conversationId: String)

    fun wcall_config_update(inst: Handle, error: Int, jsonString: String)

    fun wcall_library_version(): String

    fun wcall_init(env: Int): Int

    fun wcall_set_log_handler(logHandler: LogHandler, arg: Pointer?)

    fun wcall_end(inst: Handle, conversationId: String)

    fun wcall_close()

    fun wcall_set_mute(inst: Handle, muted: Int)

    fun wcall_sft_resp(
        inst: Handle,
        error: Int,
        data: ByteArray,
        length: Int,
        ctx: Pointer?
    )

    fun wcall_recv_msg(
        inst: Handle,
        msg: ByteArray,
        len: Int,
        curr_time: Uint32_t,
        msg_time: Uint32_t,
        convId: String,
        userId: String,
        clientId: String
    ): Int

    fun wcall_resp(
        inst: Handle,
        status: Int,
        reason: String,
        arg: Pointer?
    ): Int

    fun wcall_request_video_streams(
        inst: Handle,
        convId: String,
        mode: Int,
        json: String
    )

    fun wcall_set_participant_changed_handler(
        inst: Handle,
        wcall_participant_changed_h: ParticipantChangedHandler,
        arg: Pointer?
    )

    companion object {
        val INSTANCE by lazy { Native.load("avs", Calling::class.java)!! }
    }
}

package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.callbacks.MissedCallHandler
import com.wire.kalium.calling.types.Uint32_t
import com.wire.kalium.logic.callingLogger

object OnMissedCall : MissedCallHandler {
    override fun onMissedCall(conversationId: String, messageTime: Uint32_t, userId: String, isVideoCall: Boolean, arg: Pointer?) {
        // NOTHING TO DO | This callback is not triggered by AVS
        callingLogger.i("[onMissedCall] - conversationId: $conversationId | userId: $userId")
    }
}

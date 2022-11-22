package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.CallClosedReason
import com.wire.kalium.calling.callbacks.CloseCallHandler
import com.wire.kalium.calling.types.Uint32_t
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.feature.call.CallStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.wire.kalium.logger.obfuscateId

@Suppress("LongParameterList")
class OnCloseCall(
    private val callRepository: CallRepository,
    private val scope: CoroutineScope,
    private val qualifiedIdMapper: QualifiedIdMapper
) : CloseCallHandler {
    override fun onClosedCall(
        reason: Int,
        conversationId: String,
        messageTime: Uint32_t,
        userId: String,
        clientId: String?,
        arg: Pointer?
    ) {
        callingLogger.i(
            "[OnCloseCall] -> ConversationId: ${conversationId.obfuscateId()} |" +
                    " UserId: ${userId.obfuscateId()} | Reason: $reason"
        )

        val avsReason = CallClosedReason.fromInt(value = reason)
        val callStatus = getCallStatusFromCloseReason(avsReason)
        val conversationIdWithDomain = qualifiedIdMapper.fromStringToQualifiedID(conversationId)
        scope.launch {
            callRepository.updateCallStatusById(
                conversationIdString = conversationIdWithDomain.toString(),
                status = callStatus
            )
            callingLogger.i("[OnCloseCall] -> ConversationId: ${conversationId.obfuscateId()} | callStatus: $callStatus")
        }

    }

    private fun getCallStatusFromCloseReason(reason: CallClosedReason): CallStatus = when (reason) {
        CallClosedReason.STILL_ONGOING -> CallStatus.STILL_ONGOING
        CallClosedReason.CANCELLED -> CallStatus.MISSED
        CallClosedReason.REJECTED -> CallStatus.REJECTED
        else -> {
            CallStatus.CLOSED
        }
    }

}

package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.CallClosedReason
import com.wire.kalium.calling.CallClosedReason.STILL_ONGOING
import com.wire.kalium.calling.callbacks.CloseCallHandler
import com.wire.kalium.calling.types.Uint32_t
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.feature.call.CallStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Suppress("LongParameterList")
class OnCloseCall(
    private val callRepository: CallRepository,
    private val scope: CoroutineScope,
    private val qualifiedIdMapper: QualifiedIdMapper
) : CloseCallHandler {
    override fun onClosedCall(
        reason: Int,
        conversationIdString: String,
        messageTime: Uint32_t,
        userId: String,
        clientId: String?,
        arg: Pointer?
    ) {
        callingLogger.i("OnCloseCall -> ConversationId $conversationIdString from user $userId , CLOSED for reason: $reason")

        val avsReason = CallClosedReason.fromInt(value = reason)
        val callStatus = if (avsReason === STILL_ONGOING) CallStatus.STILL_ONGOING else CallStatus.CLOSED
        val conversationIdWithDomain = qualifiedIdMapper.fromStringToQualifiedID(conversationIdString)
        scope.launch {
            callRepository.updateCallStatusById(
                conversationIdString = conversationIdWithDomain.toString(),
                status = callStatus
            )
        }

        callingLogger.i("OnCloseCall -> incoming call status for conversation $conversationIdString updated to $callStatus..")
    }
}

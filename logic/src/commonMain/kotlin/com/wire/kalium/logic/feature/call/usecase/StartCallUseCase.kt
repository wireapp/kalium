package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallType
import com.wire.kalium.logic.data.call.ConversationType
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager

class StartCallUseCase(private val callManager: Lazy<CallManager>) {

    suspend operator fun invoke(
        conversationId: ConversationId,
        callType: CallType = CallType.AUDIO,
        conversationType: ConversationType,
        isAudioCbr: Boolean = false
    ) {
        callManager.value.startCall(
            conversationId = conversationId,
            callType = callType,
            conversationType = conversationType,
            isAudioCbr = isAudioCbr
        )
    }
}

package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.callbacks.IncomingCallHandler
import com.wire.kalium.calling.types.Uint32_t
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallMapper
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.ConversationType
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.id.toConversationId
import com.wire.kalium.logic.feature.call.CallStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

//TODO(testing): create unit test
class OnIncomingCall(
    private val callRepository: CallRepository,
    private val callMapper: CallMapper,
    private val federatedIdMapper: FederatedIdMapper,
    private val scope: CoroutineScope
) : IncomingCallHandler {
    override fun onIncomingCall(
        conversationId: String,
        messageTime: Uint32_t,
        userId: String,
        clientId: String,
        isVideoCall: Boolean,
        shouldRing: Boolean,
        conversationType: Int,
        arg: Pointer?
    ) {
        callingLogger.i("OnIncomingCall -> incoming call from $userId in conversation $conversationId at $messageTime")
        val conversationType = callMapper.fromIntToConversationType(conversationType)
        val isMuted = conversationType == ConversationType.Conference
        scope.launch {
            callRepository.createCall(
                conversationId = conversationId.toConversationId(),
                status = CallStatus.INCOMING,
                callerId = federatedIdMapper.parseToFederatedId(userId),
                isMuted = isMuted,
                isCameraOn = isVideoCall
            )
        callingLogger.i("OnIncomingCall -> incoming call for conversation $conversationId added to data flow")
        }
    }
}

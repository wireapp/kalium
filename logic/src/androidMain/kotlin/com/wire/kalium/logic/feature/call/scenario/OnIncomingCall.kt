package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.callbacks.IncomingCallHandler
import com.wire.kalium.calling.types.Uint32_t
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.ConversationType
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.feature.call.CallStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// TODO(testing): create unit test
class OnIncomingCall(
    private val callRepository: CallRepository,
    private val callMapper: CallMapper,
    private val qualifiedIdMapper: QualifiedIdMapper,
    private val scope: CoroutineScope
) : IncomingCallHandler {
    override fun onIncomingCall(
        conversationIdString: String,
        messageTime: Uint32_t,
        userId: String,
        clientId: String,
        isVideoCall: Boolean,
        shouldRing: Boolean,
        conversationType: Int,
        arg: Pointer?
    ) {
        callingLogger.i(
            "[OnIncomingCall] -> ConversationId: ${conversationIdString.obfuscateId()}" +
                    " | UserId: ${userId.obfuscateId()} | shouldRing: $shouldRing"
        )
        val mappedConversationType = callMapper.fromIntToConversationType(conversationType)
        val isMuted = mappedConversationType == ConversationType.Conference
        val status = if (shouldRing) CallStatus.INCOMING else CallStatus.STILL_ONGOING
        scope.launch {
            callRepository.createCall(
                conversationId = qualifiedIdMapper.fromStringToQualifiedID(conversationIdString),
                status = status,
                callerId = qualifiedIdMapper.fromStringToQualifiedID(userId).toString(),
                isMuted = isMuted,
                isCameraOn = isVideoCall
            )
        }
    }
}

package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * This use case is responsible for muting a call.
 */
class MuteCallUseCase internal constructor(
    private val callManager: Lazy<CallManager>,
    private val callRepository: CallRepository
) {
    /**
     * We should call AVS muting method only for established call, otherwise incoming call could mute/un-mute the current call
     * @param conversationId the id of the conversation.
     */
    suspend operator fun invoke(conversationId: ConversationId) = withContext(KaliumDispatcherImpl.default) {
        callRepository.updateIsMutedById(
            conversationId = conversationId.toString(),
            isMuted = true
        )
        val activeCall = callRepository.establishedCallsFlow().first().find {
            it.conversationId == conversationId
        }
        activeCall?.let { callManager.value.muteCall(true) }
    }
}

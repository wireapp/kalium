package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

class EndCallUseCase(private val callManager: Lazy<CallManager>,
                     private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) {

    suspend operator fun invoke(conversationId: ConversationId) = withContext(dispatchers.io) {
        callManager.value.endCall(conversationId)
    }
}

package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallClient
import com.wire.kalium.logic.data.call.CallClientList
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * This use case is responsible for requesting video streams for a call to avs.
 */
class RequestVideoStreamsUseCase(
    private val callManager: Lazy<CallManager>,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) {
    /**
     * @param conversationId the id of the conversation.
     * @param clients the list of clients that should be requested for video streams.
     */
    suspend operator fun invoke(
        conversationId: ConversationId,
        clients: List<CallClient>
    ) = withContext(dispatchers.io) {
        val callClients = CallClientList(clients = clients)
        callManager.value.requestVideoStreams(conversationId, callClients)
    }
}

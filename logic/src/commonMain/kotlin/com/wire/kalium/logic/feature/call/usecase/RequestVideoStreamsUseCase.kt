package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallClient
import com.wire.kalium.logic.data.call.CallClientList
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

class RequestVideoStreamsUseCase(
    private val callManager: Lazy<CallManager>,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) {
    suspend operator fun invoke(
        conversationId: ConversationId,
        clients: List<CallClient>
    ) = withContext(dispatchers.io) {
        val callClients = CallClientList(clients = clients)
        callManager.value.requestVideoStreams(conversationId, callClients)
    }
}

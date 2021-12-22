package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.feature.conversation.GetConversationsUseCase

class ClientScope(
    clientRepository: ClientRepository
) {
    val registerClient: RegisterClientUseCase = RegisterClientUseCase(clientRepository)
}

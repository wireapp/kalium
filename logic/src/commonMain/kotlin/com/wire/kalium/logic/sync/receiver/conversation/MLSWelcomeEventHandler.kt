package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapMLSRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationEntity
import io.ktor.util.decodeBase64Bytes
import kotlinx.coroutines.flow.first

interface MLSWelcomeEventHandler {
    suspend fun handle(event: Event.Conversation.MLSWelcome)
}

internal class MLSWelcomeEventHandlerImpl(
    val mlsClientProvider: MLSClientProvider,
    val conversationDAO: ConversationDAO
) : MLSWelcomeEventHandler {
    override suspend fun handle(event: Event.Conversation.MLSWelcome) {
        mlsClientProvider.getMLSClient().flatMap { client ->
            wrapMLSRequest { client.processWelcomeMessage(event.message.decodeBase64Bytes()) }
                .flatMap { groupID ->
                    kaliumLogger.i("Created conversation from welcome message (groupID = $groupID)")

                    wrapStorageRequest {
                        if (conversationDAO.getConversationByGroupID(groupID).first() != null) {
                            // Welcome arrived after the conversation create event, updating existing conversation.
                            conversationDAO.updateConversationGroupState(ConversationEntity.GroupState.ESTABLISHED, groupID)
                            kaliumLogger.i("Updated conversation from welcome message (groupID = $groupID)")
                        }
                    }
                }
        }
    }
}

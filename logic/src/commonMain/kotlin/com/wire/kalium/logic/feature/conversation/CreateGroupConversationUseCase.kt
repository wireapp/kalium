package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.CreateConversationParam
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.datetime.Clock

class CreateGroupConversationUseCase(
    private val conversationRepository: ConversationRepository,
    private val syncManager: SyncManager,
    private val clientRepository: ClientRepository
) {
    suspend operator fun invoke(param: CreateConversationParam): Either<CoreFailure, Conversation> {
        syncManager.waitUntilLive()
        return clientRepository.currentClientId().flatMap { clientId ->
            conversationRepository.createGroupConversation(name, userIdList, options.copy(creatorClientId = clientId.value))
                .flatMap { conversation ->
                    conversationRepository.updateConversationModifiedDate(conversation.id, Clock.System.now().toString())
                        .map { conversation }
                }
        }
    }
}

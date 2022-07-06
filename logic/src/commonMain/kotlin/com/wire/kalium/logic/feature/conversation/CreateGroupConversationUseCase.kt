package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationOptions
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.datetime.Clock

sealed class CreateMLSConversationFailure : CoreFailure.FeatureFailure() {
    object MissingClientId : CreateMLSConversationFailure()
}

class CreateGroupConversationUseCase(
    private val conversationRepository: ConversationRepository,
    private val syncManager: SyncManager,
    private val clientRepository: ClientRepository
) {
    suspend operator fun invoke(name: String, userIdList: List<UserId>, options: ConversationOptions): Either<CoreFailure, Conversation> {
        syncManager.waitUntilLive()
        if (options.protocol == ConversationOptions.Protocol.MLS) {
            return clientRepository.currentClientId().flatMap { clientId ->
                conversationRepository.createGroupConversation(name, userIdList, options.copy(creatorClient = clientId.value))
                    .flatMap { conversation ->
                        conversationRepository.updateConversationModifiedDate(conversation.id, Clock.System.now().toString())
                            .map { conversation }
                    }
            }.onFailure {
                return Either.Left(CreateMLSConversationFailure.MissingClientId)
            }
        } else
            return conversationRepository.createGroupConversation(name, userIdList, options)
                .flatMap { conversation ->
                    conversationRepository.updateConversationModifiedDate(conversation.id, Clock.System.now().toString())
                        .map { conversation }
                }
    }
}

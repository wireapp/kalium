package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo.MLS.GroupState
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isMlsStaleMessage

/**
 * Send an external add proposal to join all MLS conversations which the user is member
 * of but has not yet joined the corresponding MLS group.
 */
class JoinExistingMLSConversationsUseCase(
    val conversationRepository: ConversationRepository
) {
    suspend operator fun invoke(): Either<CoreFailure, Unit> =
        conversationRepository.getConversationsByGroupState(GroupState.PENDING_JOIN).flatMap { pendingConversations ->
            kaliumLogger.d("Requesting to re-join ${pendingConversations.size} existing MLS conversation(s)")

            return pendingConversations.map { conversation ->
                requestToJoinMLSGroupAndRetry(conversation)
            }.foldToEitherWhileRight(Unit) { _, _ ->
                Either.Right(Unit)
            }
        }

    private suspend fun requestToJoinMLSGroupAndRetry(conversation: Conversation): Either<CoreFailure, Unit> =
        conversationRepository.requestToJoinMLSGroup(conversation)
            .onFailure { failure ->
                if (failure is NetworkFailure.ServerMiscommunication && failure.kaliumException is KaliumException.InvalidRequestError) {
                    if (failure.kaliumException.isMlsStaleMessage()) {
                        kaliumLogger.w("Epoch out of date for conversation ${conversation.id}, re-fetching and re-trying")

                        // Re-fetch current epoch and try again
                        conversationRepository.fetchConversation(conversation.id).flatMap {
                            conversationRepository.observeById(conversation.id).flatMap {
                                requestToJoinMLSGroupAndRetry(conversation)
                            }
                        }
                    } else {
                        Either.Left(failure)
                    }
                }
            }
}

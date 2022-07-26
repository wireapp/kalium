package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo.MLS.GroupState
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.flatMapLeft
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isMlsStaleMessage
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

/**
 * Send an external add proposal to join all MLS conversations which the user is member
 * of but has not yet joined the corresponding MLS group.
 */
class JoinExistingMLSConversationsUseCase(
    val conversationRepository: ConversationRepository,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) {
    private val dispatcher = kaliumDispatcher.io
    private val scope = CoroutineScope(dispatcher)

    suspend operator fun invoke(): Either<CoreFailure, Unit> =
        conversationRepository.getConversationsByGroupState(GroupState.PENDING_JOIN).flatMap { pendingConversations ->
            kaliumLogger.d("Requesting to re-join ${pendingConversations.size} existing MLS conversation(s)")

            return pendingConversations.map { conversation ->
                scope.async {
                    requestToJoinMLSGroupAndRetry(conversation)
                }
            }.map {
                it.await()
            }.foldToEitherWhileRight(Unit) { value, _ ->
                value
            }
        }

    private suspend fun requestToJoinMLSGroupAndRetry(conversation: Conversation): Either<CoreFailure, Unit> =
        conversationRepository.requestToJoinMLSGroup(conversation)
            .flatMapLeft { failure ->
                if (failure is NetworkFailure.ServerMiscommunication && failure.kaliumException is KaliumException.InvalidRequestError) {
                    if (failure.kaliumException.isMlsStaleMessage()) {
                        kaliumLogger.w("Epoch out of date for conversation ${conversation.id}, re-fetching and re-trying")

                        // Re-fetch current epoch and try again
                        return conversationRepository.fetchConversation(conversation.id).flatMap {
                            conversationRepository.observeById(conversation.id).flatMap {
                                requestToJoinMLSGroupAndRetry(conversation)
                            }
                        }
                    }
                }
                Either.Left(failure)
            }
}

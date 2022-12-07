package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo.MLS.GroupState
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.flatMapLeft
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isMlsMissingGroupInfo
import com.wire.kalium.network.exceptions.isMlsStaleMessage
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

/**
 * Send an external commit to join all MLS conversations for which the user is a member,
 * but has not yet joined the corresponding MLS group.
 */
interface JoinExistingMLSConversationsUseCase {
    suspend operator fun invoke(): Either<CoreFailure, Unit>
}

@Suppress("LongParameterList")
class JoinExistingMLSConversationsUseCaseImpl(
    private val featureSupport: FeatureSupport,
    private val conversationApi: ConversationApi,
    private val clientRepository: ClientRepository,
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : JoinExistingMLSConversationsUseCase {
    private val dispatcher = kaliumDispatcher.io
    private val scope = CoroutineScope(dispatcher)

    override suspend operator fun invoke(): Either<CoreFailure, Unit> =
        if (!featureSupport.isMLSSupported ||
            !clientRepository.hasRegisteredMLSClient().getOrElse(false)) {
            kaliumLogger.d("Skip re-join existing MLS conversation(s), since MLS is not supported.")
            Either.Right(Unit)
        } else {
            conversationRepository.getConversationsByGroupState(GroupState.PENDING_JOIN).flatMap { pendingConversations ->
                kaliumLogger.d("Requesting to re-join ${pendingConversations.size} existing MLS conversation(s)")

                return pendingConversations.map { conversation ->
                    scope.async {
                        joinOrEstablishMLSGroupAndRetry(conversation)
                    }
                }.map {
                    it.await()
                }.foldToEitherWhileRight(Unit) { value, _ ->
                    value
                }
            }
        }

    private suspend fun joinOrEstablishMLSGroup(conversation: Conversation): Either<CoreFailure, Unit> {
        return if (conversation.protocol is Conversation.ProtocolInfo.MLS) {
            if (conversation.protocol.epoch == 0UL) {
                if (
                    conversation.type == Conversation.Type.GLOBAL_TEAM ||
                    conversation.type == Conversation.Type.SELF
                ) {
                    kaliumLogger.i("Establish group for ${conversation.type}")
                    mlsConversationRepository.establishMLSGroup(
                        conversation.protocol.groupId,
                        emptyList()
                    )
                 } else {
                    Either.Right(Unit)
                }
            } else {
                wrapApiRequest {
                    conversationApi.fetchGroupInfo(idMapper.toApiModel(conversation.id))
                }.flatMap { groupInfo ->
                    mlsConversationRepository.joinGroupByExternalCommit(
                        conversation.protocol.groupId,
                        groupInfo
                    )
                }
            }
        } else {
            Either.Right(Unit)
        }
    }

    private suspend fun joinOrEstablishMLSGroupAndRetry(conversation: Conversation): Either<CoreFailure, Unit> =
        joinOrEstablishMLSGroup(conversation)
            .flatMapLeft { failure ->
                if (failure is NetworkFailure.ServerMiscommunication && failure.kaliumException is KaliumException.InvalidRequestError) {
                    if (failure.kaliumException.isMlsStaleMessage()) {
                        kaliumLogger.w("Epoch out of date for conversation ${conversation.id}, re-fetching and re-trying")
                        // Re-fetch current epoch and try again
                        conversationRepository.fetchConversation(conversation.id).flatMap {
                            conversationRepository.detailsById(conversation.id).flatMap { conversation ->
                                joinOrEstablishMLSGroup(conversation)
                            }
                        }
                    } else if (failure.kaliumException.isMlsMissingGroupInfo()) {
                        kaliumLogger.w("conversation has no group info, ignoring...")
                        Either.Right(Unit)
                    } else {
                        Either.Left(failure)
                    }
                } else {
                    Either.Left(failure)
                }
            }
}

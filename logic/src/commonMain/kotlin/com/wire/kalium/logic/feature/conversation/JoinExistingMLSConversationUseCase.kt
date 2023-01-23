package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.flatMapLeft
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isMlsMissingGroupInfo
import com.wire.kalium.network.exceptions.isMlsStaleMessage
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Send an external commit to join all MLS conversations for which the user is a member,
 * but has not yet joined the corresponding MLS group.
 */
interface JoinExistingMLSConversationUseCase {
    suspend operator fun invoke(conversationId: ConversationId): Either<CoreFailure, Unit>
}

@Suppress("LongParameterList")
class JoinExistingMLSConversationUseCaseImpl(
    private val featureSupport: FeatureSupport,
    private val conversationApi: ConversationApi,
    private val clientRepository: ClientRepository,
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : JoinExistingMLSConversationUseCase {
    private val dispatcher = kaliumDispatcher.io

    override suspend operator fun invoke(conversationId: ConversationId): Either<CoreFailure, Unit> =
        if (!featureSupport.isMLSSupported ||
            !clientRepository.hasRegisteredMLSClient().getOrElse(false)
        ) {
            kaliumLogger.d("Skip re-join existing MLS conversation(s), since MLS is not supported.")
            Either.Right(Unit)
        } else {
            conversationRepository.baseInfoById(conversationId).fold({
                Either.Left(StorageFailure.DataNotFound)
            },{ conversation ->
                withContext(dispatcher) {
                    joinOrEstablishMLSGroupAndRetry(conversation)
                }
            })
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
                    conversationApi.fetchGroupInfo(conversation.id.toApi())
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

    private suspend fun joinOrEstablishMLSGroupAndRetry(
        conversation: Conversation
    ): Either<CoreFailure, Unit> =
        joinOrEstablishMLSGroup(conversation)
            .flatMapLeft { failure ->
                if (failure is NetworkFailure.ServerMiscommunication && failure.kaliumException is KaliumException.InvalidRequestError) {
                    if (failure.kaliumException.isMlsStaleMessage()) {
                        kaliumLogger.w("Epoch out of date for conversation ${conversation.id}, re-fetching and re-trying")
                        // Re-fetch current epoch and try again
                        conversationRepository.fetchConversation(conversation.id).flatMap {
                            conversationRepository.baseInfoById(conversation.id).flatMap { conversation ->
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

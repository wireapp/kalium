package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.SubconversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.flatMapLeft
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageFailureHandler
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageFailureResolution
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageUnpacker
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.base.authenticated.conversation.SubconversationDeleteRequest
import com.wire.kalium.network.api.base.authenticated.conversation.SubconversationResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isMlsStaleMessage
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import kotlin.time.Duration

/**
 * Join a sub-conversation of a MLS conversation
 */
interface JoinSubconversationUseCase {
    suspend operator fun invoke(conversationId: ConversationId, subconversationId: SubconversationId): Either<CoreFailure, Unit>
}

internal class JoinSubconversationUseCaseImpl(
    private val conversationApi: ConversationApi,
    private val mlsConversationRepository: MLSConversationRepository,
    private val subconversationRepository: SubconversationRepository,
    private val mlsMessageUnpacker: MLSMessageUnpacker,
) : JoinSubconversationUseCase {
    override suspend operator fun invoke(
        conversationId: ConversationId,
        subconversationId: SubconversationId
    ): Either<CoreFailure, Unit> =
        joinOrEstablishSubconversationAndRetry(conversationId, subconversationId)
    private suspend fun joinOrEstablishSubconversation(
        conversationId: ConversationId,
        subconversationId: SubconversationId
    ): Either<CoreFailure, Unit> =
        wrapApiRequest {
            conversationApi.fetchSubconversationDetails(conversationId.toApi(), subconversationId.toApi())
        }.flatMap { subconversationDetails ->
            joinOrEstablishWithSubconversationDetails(subconversationDetails).onSuccess {
                subconversationRepository.insertSubconversation(
                    conversationId,
                    subconversationId,
                    GroupID(subconversationDetails.groupId)
                )
            }
        }

    private suspend fun joinOrEstablishWithSubconversationDetails(
        subconversationDetails: SubconversationResponse
    ): Either<CoreFailure, Unit> =
        if (subconversationDetails.epoch > INITIAL_EPOCH) {
            if (subconversationDetails.timeElapsedSinceLastEpochChange().inWholeHours > STALE_EPOCH_DURATION_IN_HOURS) {
                wrapApiRequest {
                    conversationApi.deleteSubconversation(
                        subconversationDetails.parentId,
                        subconversationDetails.id,
                        SubconversationDeleteRequest(
                            subconversationDetails.epoch,
                            subconversationDetails.groupId
                        )
                    )
                }.flatMap {
                    mlsConversationRepository.establishMLSGroup(
                        GroupID(subconversationDetails.groupId),
                        emptyList()
                    )
                }
            } else {
                wrapApiRequest {
                    conversationApi.fetchSubconversationGroupInfo(
                        subconversationDetails.parentId,
                        subconversationDetails.id
                    )
                }.flatMap { groupInfo ->
                    mlsConversationRepository.joinGroupByExternalCommit(
                        GroupID(subconversationDetails.groupId),
                        groupInfo

                    ).flatMapLeft {
                        if (MLSMessageFailureHandler.handleFailure(it) is MLSMessageFailureResolution.Ignore) {
                            Either.Right(Unit)
                        } else {
                            Either.Left(it)
                        }
                    }
                }
            }
        } else {
            mlsConversationRepository.establishMLSGroup(GroupID(subconversationDetails.groupId), emptyList())
        }

    private suspend fun joinOrEstablishSubconversationAndRetry(
        conversationId: ConversationId,
        subconversationId: SubconversationId
    ): Either<CoreFailure, Unit> =
        joinOrEstablishSubconversation(conversationId, subconversationId)
            .flatMapLeft { failure ->
                if (failure is NetworkFailure.ServerMiscommunication && failure.kaliumException is KaliumException.InvalidRequestError) {
                    if (failure.kaliumException.isMlsStaleMessage()) {
                        kaliumLogger.w("Epoch out of date for conversation $conversationId, re-fetching and re-trying")
                        joinOrEstablishSubconversation(conversationId, subconversationId)
                    } else {
                        Either.Left(failure)
                    }
                } else {
                    Either.Left(failure)
                }
            }

    companion object {
        const val INITIAL_EPOCH = 0UL
        const val STALE_EPOCH_DURATION_IN_HOURS = 24
    }

}
private fun Instant.timeElapsedUntilNow(): Duration =
    Clock.System.now().minus(this)

private fun SubconversationResponse.timeElapsedSinceLastEpochChange(): Duration =
    epochTimestamp?.toInstant()?.timeElapsedUntilNow() ?: Duration.ZERO

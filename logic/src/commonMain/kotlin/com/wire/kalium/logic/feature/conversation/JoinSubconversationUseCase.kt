package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.flatMapLeft
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.base.authenticated.conversation.SubconversationDeleteRequest
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isMlsStaleMessage
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import kotlin.time.Duration

interface JoinSubconversationUseCase {
    suspend operator fun invoke(conversationId: ConversationId, subconversationId: String): Either<CoreFailure, Unit>
}

class JoinSubconversationUseCaseImpl(
    val conversationApi: ConversationApi,
    val mlsConversationRepository: MLSConversationRepository
) : JoinSubconversationUseCase {
    override suspend operator fun invoke(conversationId: ConversationId, subconversationId: String): Either<CoreFailure, Unit> =
        joinOrEstablishSubconversationAndRetry(conversationId, subconversationId)
    suspend fun joinOrEstablishSubconversation(conversationId: ConversationId, subconversationId: String): Either<CoreFailure, Unit> =
        wrapApiRequest {
            conversationApi.fetchSubconversationDetails(conversationId.toApi(), subconversationId)
        }.flatMap { subconversationDetails ->
            if (subconversationDetails.epoch > 0UL) {
                if (subconversationDetails.epochTimestamp?.toInstant()?.timeElapsedUntilNow()?.inWholeHours ?: 0 > 24) {
                    wrapApiRequest {
                        conversationApi.deleteSubconversation(conversationId.toApi(), subconversationId, SubconversationDeleteRequest(
                            subconversationDetails.epoch,
                            subconversationDetails.groupId
                        ))
                    }.flatMap {
                        mlsConversationRepository.establishMLSGroup(GroupID(subconversationDetails.groupId), emptyList())
                    }
                } else{
                    wrapApiRequest {
                        conversationApi.fetchSubconversationGroupInfo(conversationId.toApi(), subconversationId)
                    }.flatMap { groupInfo ->
                        mlsConversationRepository.joinGroupByExternalCommit(GroupID(subconversationDetails.groupId), groupInfo)
                    }
                }
            } else {
                mlsConversationRepository.establishMLSGroup(GroupID(subconversationDetails.groupId), emptyList())
            }
        }

    private suspend fun joinOrEstablishSubconversationAndRetry(
        conversationId: ConversationId,
        subconversationId: String
    ): Either<CoreFailure, Unit> =
        joinOrEstablishSubconversation(conversationId, subconversationId)
            .flatMapLeft { failure ->
                if (failure is NetworkFailure.ServerMiscommunication && failure.kaliumException is KaliumException.InvalidRequestError) {
                    if (failure.kaliumException.isMlsStaleMessage()) {
                        kaliumLogger.w("Epoch out of date for conversation ${conversationId}, re-fetching and re-trying")
                        // Try again
                        joinOrEstablishSubconversation(conversationId, subconversationId)
                    } else {
                        Either.Left(failure)
                    }
                } else {
                    Either.Left(failure)
                }
            }

}
private fun Instant.timeElapsedUntilNow(): Duration =
    minus(Clock.System.now())




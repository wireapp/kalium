package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo.MLS.GroupState
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger

sealed class RecoverMLSConversationsResult {
    object Success : RecoverMLSConversationsResult()
    class Failure(val failure: CoreFailure) : RecoverMLSConversationsResult()
}

/**
 *Iterate over all MLS Established conversations after 404 sync error and
 * check for out of sync epochs, if out of sync then it tries to re-join.
 */
interface RecoverMLSConversationsUseCase {
    suspend operator fun invoke(): RecoverMLSConversationsResult
}

@Suppress("LongParameterList")
class RecoverMLSConversationsUseCaseImpl(
    private val featureSupport: FeatureSupport,
    private val clientRepository: ClientRepository,
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val joinExistingMLSConversationUseCase: JoinExistingMLSConversationUseCase,
) : RecoverMLSConversationsUseCase {
    override suspend operator fun invoke(): RecoverMLSConversationsResult =
        if (!featureSupport.isMLSSupported ||
            !clientRepository.hasRegisteredMLSClient().getOrElse(false)
        ) {
            kaliumLogger.d("Skip attempting to recover established MLS conversation(s), since MLS is not supported.")
            RecoverMLSConversationsResult.Success
        } else {
            conversationRepository.getConversationsByGroupState(GroupState.ESTABLISHED)
                .flatMap { groups ->
                    groups.map { recoverMLSGroup(it) }
                        .foldToEitherWhileRight(Unit) { value, _ -> value }
                }.fold(
                    { RecoverMLSConversationsResult.Failure(it) },
                    { RecoverMLSConversationsResult.Success }
                )
        }

    private suspend fun recoverMLSGroup(conversation: Conversation): Either<CoreFailure, Unit> {
        return if (conversation.protocol is Conversation.ProtocolInfo.MLS) {
            mlsConversationRepository.isGroupOutOfSync(conversation.protocol.groupId, conversation.protocol.epoch)
                .fold({ checkEpochFailure ->
                    Either.Left(checkEpochFailure)
                }, { isGroupOutOfSync ->
                    if (isGroupOutOfSync) {
                        joinExistingMLSConversationUseCase(conversation.id).onFailure { joinFailure ->
                            Either.Left(joinFailure)
                        }
                    } else {
                        Either.Right(Unit)
                    }
                })
        } else {
            Either.Right(Unit)
        }
    }
}

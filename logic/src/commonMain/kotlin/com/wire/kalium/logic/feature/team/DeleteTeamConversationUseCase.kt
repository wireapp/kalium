package com.wire.kalium.logic.feature.team

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.feature.SelfTeamIdProvider
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map

fun interface DeleteTeamConversationUseCase {

    /**
     * This use case will allow a group conversation creator (only available for team accounts)
     * delete a conversation for everyone in the group
     *
     * @param conversationId the group conversation to be deleted
     * @return [Result] indicating operation success or failure
     */
    suspend operator fun invoke(conversationId: ConversationId): Result
}

internal class DeleteTeamConversationUseCaseImpl(
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val teamRepository: TeamRepository,
    private val conversationRepository: ConversationRepository,
) : DeleteTeamConversationUseCase {

    override suspend fun invoke(conversationId: ConversationId): Result {
        return selfTeamIdProvider()
            .map {
                it ?: return Result.Failure.NoTeamFailure
            }
            .flatMap { teamId ->
                teamRepository.deleteConversation(conversationId, teamId)
            }.fold({
                Result.Failure.GenericFailure(it)
            }, {
                conversationRepository.deleteConversation(conversationId)
                Result.Success
            })
    }
}

sealed class Result {
    object Success : Result()
    sealed class Failure : Result() {
        class GenericFailure(val coreFailure: CoreFailure) : Failure()
        object NoTeamFailure : Result()
    }

}

package com.wire.kalium.logic.feature.team

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.functional.fold
import kotlinx.coroutines.flow.firstOrNull

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
    val getSelfTeam: GetSelfTeamUseCase,
    val teamRepository: TeamRepository
) : DeleteTeamConversationUseCase {

    override suspend fun invoke(conversationId: ConversationId): Result {
        val teamId = getSelfTeam().firstOrNull()?.id
        return teamId?.let {
            teamRepository.deleteConversation(conversationId, teamId)
                .fold({
                    Result.Failure.GenericFailure(it)
                }, {
                    Result.Success
                })
        } ?: Result.Failure.NoTeamFailure
    }
}

sealed class Result {
    object Success : Result()
    sealed class Failure : Result() {
        class GenericFailure(val coreFailure: CoreFailure) : Failure()
        object NoTeamFailure : Result()
    }

}

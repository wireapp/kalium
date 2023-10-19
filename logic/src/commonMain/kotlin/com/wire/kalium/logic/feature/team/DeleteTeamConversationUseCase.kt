/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

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
    data object Success : Result()
    sealed class Failure : Result() {
        data class GenericFailure(val coreFailure: CoreFailure) : Failure()
        data object NoTeamFailure : Result()
    }

}

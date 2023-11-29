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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.http.HttpStatusCode

/**
 * This use case will add a member(s) to a given conversation.
 */
interface AddMemberToConversationUseCase {
    /**
     * @param conversationId the id of the conversation
     * @param userIdList the list of user ids to add to the conversation
     * @return the [Result] indicating a successful operation, otherwise a [CoreFailure]
     */
    suspend operator fun invoke(conversationId: ConversationId, userIdList: List<UserId>): Result

    sealed interface Result {
        data object Success : Result
        data class Failure(val cause: CoreFailure) : Result
    }
}

internal class AddMemberToConversationUseCaseImpl(
    private val conversationGroupRepository: ConversationGroupRepository,
    private val userRepository: UserRepository
) : AddMemberToConversationUseCase {
    override suspend fun invoke(conversationId: ConversationId, userIdList: List<UserId>): AddMemberToConversationUseCase.Result {
        return conversationGroupRepository.addMembers(userIdList, conversationId)
            .onFailure {
                when (it) {
                    is NetworkFailure.ServerMiscommunication -> {
                        if (it.kaliumException is KaliumException.InvalidRequestError &&
                            it.kaliumException.errorResponse.code == HttpStatusCode.Forbidden.value) {
                            handle403Error(userIdList)
                        }
                    }

                    else -> { /* do nothing */ }
                }
            }
            .fold({
                AddMemberToConversationUseCase.Result.Failure(it)
            }, {
                AddMemberToConversationUseCase.Result.Success
            })
    }

    private suspend fun handle403Error(userIdList: List<UserId>) {
        userRepository.fetchUsersByIds(userIdList.toSet())
    }
}

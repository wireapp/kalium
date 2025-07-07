/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import io.mockative.Mockable

@Mockable
internal interface FetchMLSOneToOneConversationUseCase {
    suspend operator fun invoke(userId: UserId): Either<CoreFailure, Conversation>
}

internal class FetchMLSOneToOneConversationUseCaseImpl(
    private val selfUserId: UserId,
    private val conversationRepository: ConversationRepository,
    private val persistConversations: PersistConversationsUseCase,
    private val conversationMapper: ConversationMapper = MapperProvider.conversationMapper(selfUserId),
) : FetchMLSOneToOneConversationUseCase {

    override suspend fun invoke(userId: UserId): Either<CoreFailure, Conversation> {
        return conversationRepository.fetchMlsOneToOneConversation(userId)
            .map { addOtherMemberIfMissing(it, userId) }
            .flatMap { response ->
                persistConversations(
                    conversations = listOf(response),
                    invalidateMembers = false
                )
                    .flatMap {
                        conversationRepository.getConversationById(response.id.toModel()).map {
                            it.copy(mlsPublicKeys = conversationMapper.fromApiModel(response.publicKeys))
                        }
                    }
            }
    }

    private fun addOtherMemberIfMissing(
        conversationResponse: ConversationResponse,
        otherMemberId: UserId
    ): ConversationResponse {
        val currentOtherMembers = conversationResponse.members.otherMembers
        val hasOtherUser = currentOtherMembers.any { it.id == otherMemberId.toApi() }

        if (hasOtherUser) return conversationResponse

        val updatedOtherMembers = currentOtherMembers + ConversationMemberDTO.Other(
            id = otherMemberId.toApi(),
            conversationRole = "",
            service = null
        )

        return conversationResponse.copy(
            members = conversationResponse.members.copy(
                otherMembers = updatedOtherMembers
            )
        )
    }

}

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
package com.wire.kalium.logic.data.conversation

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponse
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.util.DateTimeUtil

/**
 * Handles the addition of members to a new conversation and the related system messages when a conversation is started.
 * Either all users are added or some of them could fail to be added.
 *
 * TODO(offline backend branch): And add failed members handling in api v4
 */
internal interface NewConversationMembersRepository {
    suspend fun persistMembersAdditionToTheConversation(
        conversationId: ConversationIDEntity,
        conversationResponse: ConversationResponse,
    ): Either<CoreFailure, Unit>
}

internal class NewConversationMembersRepositoryImpl(
    private val persistMessage: PersistMessageUseCase,
    private val conversationDAO: ConversationDAO,
    private val selfUserId: UserId,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val memberMapper: MemberMapper = MapperProvider.memberMapper()
) : NewConversationMembersRepository {

    override suspend fun persistMembersAdditionToTheConversation(
        conversationId: ConversationIDEntity,
        conversationResponse: ConversationResponse,
    ) = run {
        persistMembers(conversationResponse).flatMap {
            if (conversationResponse.members.otherMembers.isEmpty()) {
                Either.Right(Unit)
            } else {
                val messageStartedWithMembers = Message.System(
                    uuid4().toString(),
                    MessageContent.MemberChange.CreationAdded(mapOtherMembersIdFromResponse(conversationResponse)),
                    conversationId.toModel(),
                    DateTimeUtil.currentIsoDateTimeString(),
                    selfUserId,
                    Message.Status.SENT,
                    Message.Visibility.VISIBLE,
                    expirationData = null
                )
                persistMessage(messageStartedWithMembers)
            }
        }
    }

    private suspend fun persistMembers(
        conversationResponse: ConversationResponse
    ): Either<CoreFailure, Unit> {
        return wrapStorageRequest {
            val conversationId = idMapper.fromApiToDao(conversationResponse.id)
            conversationDAO.insertMembersWithQualifiedId(
                memberMapper.fromApiModelToDaoModel(conversationResponse.members),
                conversationId
            )
        }
    }

    private fun mapOtherMembersIdFromResponse(conversationResponse: ConversationResponse): List<UserId> =
        memberMapper.fromApiModel(conversationResponse.members).otherMembers.map { it.id }

}

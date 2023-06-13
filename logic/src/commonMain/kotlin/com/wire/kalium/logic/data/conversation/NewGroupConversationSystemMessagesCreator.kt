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
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.user.IsSelfATeamMemberUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ReceiptMode
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.util.DateTimeUtil

/**
 * This class is responsible to generate system messages for new group conversations.
 * This can be orchestrated by different components that creates a new group conversation, ie: Events, UseCases, Repositories.
 */
internal interface NewGroupConversationSystemMessagesCreator {
    suspend fun conversationStarted(conversation: ConversationEntity): Either<CoreFailure, Unit>
    suspend fun conversationStarted(conversation: ConversationResponse): Either<CoreFailure, Unit>
    suspend fun conversationReadReceiptStatus(conversation: Conversation): Either<CoreFailure, Unit>
    suspend fun conversationReadReceiptStatus(conversation: ConversationResponse): Either<CoreFailure, Unit>
    suspend fun conversationResolvedMembersAddedAndFailed(
        conversationId: ConversationIDEntity,
        conversationResponse: ConversationResponse
    ): Either<CoreFailure, Unit>
}

internal class NewGroupConversationSystemMessagesCreatorImpl(
    private val persistMessage: PersistMessageUseCase,
    private val isSelfATeamMember: IsSelfATeamMemberUseCase,
    private val qualifiedIdMapper: QualifiedIdMapper,
    private val selfUserId: UserId,
    private val memberMapper: MemberMapper = MapperProvider.memberMapper()
) : NewGroupConversationSystemMessagesCreator {

    override suspend fun conversationStarted(conversation: ConversationEntity) = run {
        if (conversation.type != ConversationEntity.Type.GROUP) {
            return Either.Right(Unit)
        }
        persistConversationStartedSystemMessage(
            conversation.creatorId.let { qualifiedIdMapper.fromStringToQualifiedID(it) },
            conversation.id.toModel()
        )
    }

    override suspend fun conversationStarted(conversation: ConversationResponse) = run {
        if (conversation.type != ConversationResponse.Type.GROUP) {
            return Either.Right(Unit)
        }
        persistConversationStartedSystemMessage(
            conversation.creator.let { qualifiedIdMapper.fromStringToQualifiedID(it) },
            conversation.id.toModel()
        )
    }

    private suspend fun persistConversationStartedSystemMessage(creatorId: UserId, conversationId: ConversationId) = persistMessage(
        Message.System(
            id = uuid4().toString(),
            content = MessageContent.ConversationCreated,
            conversationId = conversationId,
            date = DateTimeUtil.currentIsoDateTimeString(),
            senderUserId = creatorId,
            status = Message.Status.SENT,
            visibility = Message.Visibility.VISIBLE
        )
    )

    override suspend fun conversationReadReceiptStatus(conversation: Conversation) = run {
        if (conversation.type != Conversation.Type.GROUP || !isSelfATeamMember()) {
            return Either.Right(Unit)
        }

        persistReadReceiptSystemMessage(
            conversationId = conversation.id,
            creatorId = conversation.creatorId?.let { qualifiedIdMapper.fromStringToQualifiedID(it) } ?: selfUserId,
            receiptMode = conversation.receiptMode == Conversation.ReceiptMode.ENABLED
        )
    }

    override suspend fun conversationReadReceiptStatus(conversation: ConversationResponse) = run {
        if (conversation.type != ConversationResponse.Type.GROUP || !isSelfATeamMember()) {
            return Either.Right(Unit)
        }

        persistReadReceiptSystemMessage(
            conversationId = conversation.id.toModel(),
            creatorId = qualifiedIdMapper.fromStringToQualifiedID(conversation.creator),
            receiptMode = conversation.receiptMode == ReceiptMode.ENABLED
        )
    }

    private suspend fun persistReadReceiptSystemMessage(
        conversationId: ConversationId,
        creatorId: UserId,
        receiptMode: Boolean
    ) = persistMessage(
        Message.System(
            id = uuid4().toString(),
            content = MessageContent.NewConversationReceiptMode(receiptMode = receiptMode),
            conversationId = conversationId,
            date = DateTimeUtil.currentIsoDateTimeString(),
            senderUserId = creatorId,
            status = Message.Status.SENT,
            visibility = Message.Visibility.VISIBLE
        )
    )

    override suspend fun conversationResolvedMembersAddedAndFailed(
        conversationId: ConversationIDEntity,
        conversationResponse: ConversationResponse
    ): Either<CoreFailure, Unit> = run {
        if (conversationResponse.members.otherMembers.isEmpty()) {
            Either.Right(Unit)
        } else {
            persistMessage(
                Message.System(
                    id = uuid4().toString(),
                    content = MessageContent.MemberChange.CreationAdded(
                        memberMapper.fromApiModel(conversationResponse.members).otherMembers.map { it.id }
                    ),
                    conversationId = conversationId.toModel(),
                    date = DateTimeUtil.currentIsoDateTimeString(),
                    senderUserId = selfUserId,
                    status = Message.Status.SENT,
                    visibility = Message.Visibility.VISIBLE
                )
            )
        }
    }
}

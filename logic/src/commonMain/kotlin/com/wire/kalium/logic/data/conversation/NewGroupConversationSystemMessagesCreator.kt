/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.authenticated.conversation.ReceiptMode
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.message.LocalId
import io.mockative.Mockable
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * This class is responsible to generate system messages for new group conversations.
 * This can be orchestrated by different components that creates a new group conversation, ie: Events, UseCases, Repositories.
 */
@Mockable
internal interface NewGroupConversationSystemMessagesCreator {
    suspend fun conversationStarted(conversation: ConversationEntity): Either<CoreFailure, Unit>
    suspend fun conversationStarted(creatorId: UserId, conversation: ConversationResponse, instant: Instant): Either<CoreFailure, Unit>
    suspend fun conversationReadReceiptStatus(conversation: Conversation): Either<CoreFailure, Unit>
    suspend fun conversationReadReceiptStatus(conversation: ConversationResponse, instant: Instant): Either<CoreFailure, Unit>
    suspend fun conversationResolvedMembersAdded(
        conversationId: ConversationIDEntity,
        validUsers: List<UserId>,
        instant: Instant = Clock.System.now()
    ): Either<CoreFailure, Unit>

    suspend fun conversationFailedToAddMembers(
        conversationId: ConversationId,
        userIdList: List<UserId>,
        type: MessageContent.MemberChange.FailedToAdd.Type,
    ): Either<CoreFailure, Unit>

    suspend fun conversationStartedUnverifiedWarning(
        conversationId: ConversationId,
        instant: Instant = Clock.System.now()
    ): Either<CoreFailure, Unit>
}

internal class NewGroupConversationSystemMessagesCreatorImpl(
    private val persistMessage: PersistMessageUseCase,
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val qualifiedIdMapper: QualifiedIdMapper,
    private val selfUserId: UserId,
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

    override suspend fun conversationStarted(creatorId: UserId, conversation: ConversationResponse, instant: Instant) = run {
        if (conversation.type != ConversationResponse.Type.GROUP) {
            return Either.Right(Unit)
        }
        persistConversationStartedSystemMessage(
            creatorId,
            conversation.id.toModel(),
            instant
        )
    }

    private suspend fun persistConversationStartedSystemMessage(
        creatorId: UserId,
        conversationId: ConversationId,
        instant: Instant = Clock.System.now()
    ) = persistMessage(
        Message.System(
            id = uuid4().toString(),
            content = MessageContent.ConversationCreated,
            conversationId = conversationId,
            date = instant,
            senderUserId = creatorId,
            status = Message.Status.Sent,
            visibility = Message.Visibility.VISIBLE,
            expirationData = null
        )
    )

    override suspend fun conversationReadReceiptStatus(conversation: Conversation) = run {
        if (conversation.type !is Conversation.Type.Group || !isSelfATeamMember()) {
            return Either.Right(Unit)
        }

        persistReadReceiptSystemMessage(
            conversationId = conversation.id,
            creatorId = conversation.creatorId?.let { qualifiedIdMapper.fromStringToQualifiedID(it) } ?: selfUserId,
            receiptMode = conversation.receiptMode == Conversation.ReceiptMode.ENABLED
        )
    }

    override suspend fun conversationReadReceiptStatus(conversation: ConversationResponse, instant: Instant) = run {
        if (conversation.type != ConversationResponse.Type.GROUP || !isSelfATeamMember()) {
            return Either.Right(Unit)
        }

        persistReadReceiptSystemMessage(
            conversationId = conversation.id.toModel(),
            creatorId = conversation.creator?.let { qualifiedIdMapper.fromStringToQualifiedID(it) } ?: selfUserId,
            receiptMode = conversation.receiptMode == ReceiptMode.ENABLED,
            instant = instant
        )
    }

    private suspend fun persistReadReceiptSystemMessage(
        conversationId: ConversationId,
        creatorId: UserId,
        receiptMode: Boolean,
        instant: Instant = Clock.System.now()
    ) = persistMessage(
        Message.System(
            id = uuid4().toString(),
            content = MessageContent.NewConversationReceiptMode(receiptMode = receiptMode),
            conversationId = conversationId,
            date = instant,
            senderUserId = creatorId,
            status = Message.Status.Sent,
            visibility = Message.Visibility.VISIBLE,
            expirationData = null
        )
    )

    override suspend fun conversationResolvedMembersAdded(
        conversationId: ConversationIDEntity,
        validUsers: List<UserId>,
        instant: Instant
    ): Either<CoreFailure, Unit> = run {
        if (validUsers.isNotEmpty()) {
            persistMessage(
                Message.System(
                    id = uuid4().toString(),
                    content = MessageContent.MemberChange.CreationAdded(validUsers.toList()),
                    conversationId = conversationId.toModel(),
                    date = instant,
                    senderUserId = selfUserId,
                    status = Message.Status.Sent,
                    visibility = Message.Visibility.VISIBLE,
                    expirationData = null
                )
            )
        }
        Either.Right(Unit)
    }

    override suspend fun conversationFailedToAddMembers(
        conversationId: ConversationId,
        userIdList: List<UserId>,
        type: MessageContent.MemberChange.FailedToAdd.Type,
    ): Either<CoreFailure, Unit> = run {
        if (userIdList.isNotEmpty()) {
            persistMessage(
                Message.System(
                    uuid4().toString(),
                    MessageContent.MemberChange.FailedToAdd(userIdList, type),
                    conversationId,
                    Clock.System.now(),
                    selfUserId,
                    Message.Status.Sent,
                    Message.Visibility.VISIBLE,
                    expirationData = null
                )
            )
        }
        Either.Right(Unit)
    }

    override suspend fun conversationStartedUnverifiedWarning(
        conversationId: ConversationId,
        instant: Instant
    ): Either<CoreFailure, Unit> =
        persistMessage(
            Message.System(
                LocalId.generate(),
                MessageContent.ConversationStartedUnverifiedWarning,
                conversationId,
                instant,
                selfUserId,
                Message.Status.Sent,
                Message.Visibility.VISIBLE,
                expirationData = null
            )
        )

    private suspend fun isSelfATeamMember() = selfTeamIdProvider().fold({ false }, { it != null })
}

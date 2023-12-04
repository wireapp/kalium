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
package com.wire.kalium.logic.sync.receiver.handler.legalhold

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.client.FetchSelfClientsFromRemoteUseCase
import com.wire.kalium.logic.feature.conversation.IsConversationUnderLegalHoldUseCase
import com.wire.kalium.logic.feature.client.PersistOtherUserClientsUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

internal interface LegalHoldHandler {
    suspend fun handleEnable(legalHoldEnabled: Event.User.LegalHoldEnabled): Either<CoreFailure, Unit>
    suspend fun handleDisable(legalHoldDisabled: Event.User.LegalHoldDisabled): Either<CoreFailure, Unit>
}

internal class LegalHoldHandlerImpl internal constructor(
    private val selfUserId: UserId,
    private val persistOtherUserClients: PersistOtherUserClientsUseCase,
    private val fetchSelfClientsFromRemote: FetchSelfClientsFromRemoteUseCase,
    private val isConversationUnderLegalHold: IsConversationUnderLegalHoldUseCase,
    private val persistMessage: PersistMessageUseCase,
    private val userConfigRepository: UserConfigRepository,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val coroutineContext: CoroutineContext,
    private val coroutineScope: CoroutineScope = CoroutineScope(coroutineContext)
) : LegalHoldHandler {
    override suspend fun handleEnable(legalHoldEnabled: Event.User.LegalHoldEnabled): Either<CoreFailure, Unit> {
        kaliumLogger.i("legal hold enabled for user ${legalHoldEnabled.userId.toLogString()}")
        processEvent(selfUserId, legalHoldEnabled.userId)

        conversationRepository.getConversationsByUserId(legalHoldEnabled.userId).map {
            handleMemberSystemMessagesForConversations(
                userId = legalHoldEnabled.userId,
                conversationIds = it.map { it.id },
                updateContent = { content -> content.copy(members = content.members - selfUserId) },
                createNewContent = { MessageContent.LegalHold.DisabledForMembers(members = listOf(selfUserId)) }
            )

            it.forEach { conversation ->
                isConversationUnderLegalHold(conversation.id)
                    .map { isUnderLegalHold ->
                        if (isUnderLegalHold) {
                            conversationRepository.updateLegalHoldStatus(conversation.id, Conversation.LegalHoldStatus.ENABLED)
                        }
                    }
            }
        }

        return Either.Right(Unit)
    }

    override suspend fun handleDisable(legalHoldDisabled: Event.User.LegalHoldDisabled): Either<CoreFailure, Unit> {
        kaliumLogger.i("legal hold disabled for user ${legalHoldDisabled.userId.toLogString()}")
        processEvent(selfUserId, legalHoldDisabled.userId)

        conversationRepository.getConversationsByUserId(legalHoldDisabled.userId).map {
            handleMemberSystemMessagesForConversations(
                userId = legalHoldDisabled.userId,
                conversationIds = it.map { it.id },
                updateContent = { content -> content.copy(members = content.members - selfUserId) },
                createNewContent = { MessageContent.LegalHold.DisabledForMembers(members = listOf(selfUserId)) }
            )

            it.forEach { conversation ->
                isConversationUnderLegalHold(conversation.id)
                    .map { isUnderLegalHold ->
                        if (!isUnderLegalHold) {
                            createSystemMessage(MessageContent.LegalHold.DisabledForConversation, conversation.id)
                            conversationRepository.updateLegalHoldStatus(conversation.id, Conversation.LegalHoldStatus.DISABLED)
                        }
                    }
            }
        }

        return Either.Right(Unit)
    }

    private suspend fun processEvent(selfUserId: UserId, userId: UserId) {
        if (selfUserId == userId) {
            userConfigRepository.deleteLegalHoldRequest()
            coroutineScope.launch {
                fetchSelfClientsFromRemote()
            }
        } else {
            coroutineScope.launch {
                persistOtherUserClients(userId)
            }
        }
    }

    private suspend inline fun <reified T : MessageContent.LegalHold> handleMemberSystemMessagesForConversations(
        userId: UserId,
        conversationIds: List<ConversationId>,
        updateContent: (T) -> T,
        createNewContent: () -> T,
    ) {
        val lastMessages =
            if (userId == selfUserId) Either.Right(emptyMap()) // for self user we always create new messages
            else messageRepository.getLastMessagesForConversationIds(conversationIds)
                .map { it.filterValues { it.content is T }.mapValues { it.value.id to (it.value.content as T) } }
        lastMessages.map { lastMessagesMap ->
            conversationIds.forEach { conversationId ->
                lastMessagesMap[conversationId]?.let { (lastMessageId, lastMessageContent) ->
                        messageRepository.updateLegalHoldMessage(lastMessageId, conversationId, updateContent(lastMessageContent))
                    } ?: persistMessage(createSystemMessage(createNewContent(), conversationId))
            }
        }
    }

    private fun createSystemMessage(content: MessageContent.LegalHold, conversationId: ConversationId): Message.System =
        Message.System(
            id = uuid4().toString(),
            content = content,
            conversationId = conversationId,
            date = DateTimeUtil.currentIsoDateTimeString(),
            senderUserId = selfUserId,
            status = Message.Status.Sent,
            visibility = Message.Visibility.VISIBLE,
            expirationData = null
        )
}

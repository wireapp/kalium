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
package com.wire.kalium.logic.sync.receiver.handler.legalhold

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.util.DateTimeUtil

internal interface LegalHoldSystemMessagesHandler {
    suspend fun handleEnabledForUser(userId: UserId, systemMessageTimestampIso: String)
    suspend fun handleDisabledForUser(userId: UserId, systemMessageTimestampIso: String)
    suspend fun handleEnabledForConversation(conversationId: ConversationId, systemMessageTimestampIso: String)
    suspend fun handleDisabledForConversation(conversationId: ConversationId, systemMessageTimestampIso: String)
}

internal class LegalHoldSystemMessagesHandlerImpl(
    private val selfUserId: UserId,
    private val persistMessage: PersistMessageUseCase,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
) : LegalHoldSystemMessagesHandler {

    override suspend fun handleEnabledForUser(userId: UserId, systemMessageTimestampIso: String) = handleSystemMessagesForUser(
        userId = userId,
        newSystemMessageTimestampIso = systemMessageTimestampIso,
        update = { members -> (members + userId).distinct() },
        createNew = { MessageContent.LegalHold.ForMembers.Enabled(members = listOf(userId)) }
    )

    override suspend fun handleDisabledForUser(userId: UserId, systemMessageTimestampIso: String) = handleSystemMessagesForUser(
        userId = userId,
        newSystemMessageTimestampIso = systemMessageTimestampIso,
        update = { members -> (members + userId).distinct() },
        createNew = { MessageContent.LegalHold.ForMembers.Disabled(members = listOf(userId)) }
    )

    override suspend fun handleEnabledForConversation(conversationId: ConversationId, systemMessageTimestampIso: String) =
        handleSystemMessageForConversation(conversationId, Conversation.LegalHoldStatus.ENABLED, systemMessageTimestampIso)

    override suspend fun handleDisabledForConversation(conversationId: ConversationId, systemMessageTimestampIso: String) =
        handleSystemMessageForConversation(conversationId, Conversation.LegalHoldStatus.DISABLED, systemMessageTimestampIso)

    private suspend fun handleSystemMessageForConversation(
        conversationId: ConversationId,
        newStatus: Conversation.LegalHoldStatus,
        systemMessageTimestampIso: String = DateTimeUtil.currentIsoDateTimeString()
    ) {
        when (newStatus) {
            Conversation.LegalHoldStatus.DISABLED -> persistMessage(
                createSystemMessage(MessageContent.LegalHold.ForConversation.Disabled, conversationId, systemMessageTimestampIso)
            )
            Conversation.LegalHoldStatus.ENABLED -> persistMessage(
                createSystemMessage(MessageContent.LegalHold.ForConversation.Enabled, conversationId, systemMessageTimestampIso)
            )
            else -> { /* do nothing */ }
        }
    }

    private suspend inline fun <reified T : MessageContent.LegalHold.ForMembers> getLastLegalHoldMessagesForConversations(
        userId: UserId,
        conversations: List<Conversation>,
    ) =
        if (userId == selfUserId) Either.Right(emptyMap()) // for self user we always create new messages
        else messageRepository.getLastMessagesForConversationIds(conversations.map { it.id })
            .map { it.filterValues { it.content is T }.mapValues { it.value.id to (it.value.content as T) } }

    private suspend inline fun <reified T : MessageContent.LegalHold.ForMembers> handleSystemMessagesForUser(
        userId: UserId,
        newSystemMessageTimestampIso: String = DateTimeUtil.currentIsoDateTimeString(),
        crossinline update: (List<UserId>) -> List<UserId>,
        crossinline createNew: () -> T,
    ) {
        // get all conversations where the given user is a member
        conversationRepository.getConversationsByUserId(userId).map { conversations ->
            // get last legal hold messages for the given conversations
            getLastLegalHoldMessagesForConversations<T>(userId, conversations).map { lastMessagesMap ->
                conversations.forEach { conversation ->
                    // create or update system messages for members
                    lastMessagesMap[conversation.id]?.let { (lastMessageId, lastMessageContent) ->
                        messageRepository.updateLegalHoldMessageMembers(lastMessageId, conversation.id, update(lastMessageContent.members))
                    } ?: persistMessage(createSystemMessage(createNew(), conversation.id, newSystemMessageTimestampIso))
                }
            }
        }
    }

    private fun createSystemMessage(
        content: MessageContent.LegalHold,
        conversationId: ConversationId,
        date: String = DateTimeUtil.currentIsoDateTimeString(),
    ): Message.System =
        Message.System(
            id = uuid4().toString(),
            content = content,
            conversationId = conversationId,
            date = date,
            senderUserId = selfUserId,
            status = Message.Status.Sent,
            visibility = Message.Visibility.VISIBLE,
            expirationData = null
        )
}

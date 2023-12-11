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
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.legalhold.MembersHavingLegalHoldClientUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.util.DateTimeUtil

internal interface LegalHoldSystemMessagesHandler {
    suspend fun handleEnable(userId: UserId)
    suspend fun handleDisable(userId: UserId)
}

internal class LegalHoldSystemMessagesHandlerImpl(
    private val selfUserId: UserId,
    private val membersHavingLegalHoldClient: MembersHavingLegalHoldClientUseCase,
    private val persistMessage: PersistMessageUseCase,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
) : LegalHoldSystemMessagesHandler {

    override suspend fun handleEnable(userId: UserId) = handleSystemMessages(
            userId = userId,
            update = { members -> (members + userId).distinct() },
            createNew = { MessageContent.LegalHold.ForMembers.Enabled(members = listOf(userId)) },
            firstHandleForConversation = true
        )
    override suspend fun handleDisable(userId: UserId) = handleSystemMessages(
        userId = userId,
        update = { members -> (members + userId).distinct() },
        createNew = { MessageContent.LegalHold.ForMembers.Disabled(members = listOf(userId)) },
        firstHandleForConversation = false
    )

    private suspend inline fun <reified T : MessageContent.LegalHold.ForMembers> getLastLegalHoldMessagesForConversations(
        userId: UserId,
        conversations: List<Conversation>,
    ) =
        if (userId == selfUserId) Either.Right(emptyMap()) // for self user we always create new messages
        else messageRepository.getLastMessagesForConversationIds(conversations.map { it.id })
            .map { it.filterValues { it.content is T }.mapValues { it.value.id to (it.value.content as T) } }

    private suspend inline fun <reified T : MessageContent.LegalHold.ForMembers> handleSystemMessages(
        userId: UserId,
        crossinline update: (List<UserId>) -> List<UserId>,
        crossinline createNew: () -> T,
        firstHandleForConversation: Boolean,
    ) {
        // get all conversations where the given user is a member
        conversationRepository.getConversationsByUserId(userId).map { conversations ->
            // get last legal hold messages for the given conversations
            getLastLegalHoldMessagesForConversations<T>(userId, conversations).map { lastMessagesMap ->
                conversations.forEach { conversation ->
                    val createOrUpdateSystemMessageForMembers: suspend () -> Unit = {
                        lastMessagesMap[conversation.id]?.let { (lastMessageId, lastMessageContent) ->
                            messageRepository.updateLegalHoldMessageMembers(lastMessageId, conversation.id, update(lastMessageContent.members))
                        } ?: persistMessage(createSystemMessage(createNew(), conversation.id))
                    }
                    val createSystemMessageForConversationIfNeeded: suspend () -> Unit = {
                        membersHavingLegalHoldClient(conversation.id)
                            .map { if (it.isEmpty()) Conversation.LegalHoldStatus.DISABLED else Conversation.LegalHoldStatus.ENABLED }
                            .map { newLegalHoldStatus ->
                                if (newLegalHoldStatus != conversation.legalHoldStatus) {
                                    // if conversation legal hold status has changed, update it
                                    conversationRepository.updateLegalHoldStatus(conversation.id, newLegalHoldStatus)
                                    // if conversation legal hold status changed, create system message for it
                                    if (newLegalHoldStatus == Conversation.LegalHoldStatus.DISABLED) persistMessage(
                                        createSystemMessage(MessageContent.LegalHold.ForConversation.Disabled, conversation.id)
                                    )
                                    else if (newLegalHoldStatus == Conversation.LegalHoldStatus.ENABLED) persistMessage(
                                        createSystemMessage(MessageContent.LegalHold.ForConversation.Enabled, conversation.id)
                                    )
                                }
                            }
                    }
                    listOf(createOrUpdateSystemMessageForMembers, createSystemMessageForConversationIfNeeded)
                        .let { if (firstHandleForConversation) it.reversed() else it }
                        .forEach { it() }
                }
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

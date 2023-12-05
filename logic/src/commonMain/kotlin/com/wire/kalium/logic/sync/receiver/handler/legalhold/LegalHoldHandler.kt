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
import com.wire.kalium.logic.feature.client.PersistOtherUserClientsUseCase
import com.wire.kalium.logic.feature.conversation.MembersHavingLegalHoldClientUseCase
import com.wire.kalium.logic.feature.legalhold.LegalHoldState
import com.wire.kalium.logic.feature.legalhold.ObserveLegalHoldStateForUserUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
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
    private val observeLegalHoldStateForUser: ObserveLegalHoldStateForUserUseCase,
    private val membersHavingLegalHoldClient: MembersHavingLegalHoldClientUseCase,
    private val persistMessage: PersistMessageUseCase,
    private val userConfigRepository: UserConfigRepository,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val coroutineContext: CoroutineContext,
    private val coroutineScope: CoroutineScope = CoroutineScope(coroutineContext)
) : LegalHoldHandler {
    override suspend fun handleEnable(legalHoldEnabled: Event.User.LegalHoldEnabled): Either<CoreFailure, Unit> {
        kaliumLogger.i("legal hold enabled for user ${legalHoldEnabled.userId.toLogString()}")
        // check if the user has already been under legal hold prior to this event
        val userHasBeenUnderLegalHold = isUserUnderLegalHold(legalHoldEnabled.userId)
        // fetch and persist current clients for the given user
        processEvent(selfUserId, legalHoldEnabled.userId)
        // create system messages only if legal hold status has changed for the given user
        if (!userHasBeenUnderLegalHold) {
            handleSystemMessages(
                userId = legalHoldEnabled.userId,
                updateContent = { content -> content.copy(members = content.members - legalHoldEnabled.userId) },
                createNewContent = { MessageContent.LegalHold.EnabledForMembers(members = listOf(legalHoldEnabled.userId)) }
            )
        }

        return Either.Right(Unit)
    }

    override suspend fun handleDisable(legalHoldDisabled: Event.User.LegalHoldDisabled): Either<CoreFailure, Unit> {
        kaliumLogger.i("legal hold disabled for user ${legalHoldDisabled.userId.toLogString()}")
        // check if the user has already been under legal hold prior to this event
        val userHasBeenUnderLegalHold = isUserUnderLegalHold(legalHoldDisabled.userId)
        // fetch and persist current clients for the given user
        processEvent(selfUserId, legalHoldDisabled.userId)
        // create system messages only if legal hold status has changed for the given user
        if (userHasBeenUnderLegalHold) {
            handleSystemMessages(
                legalHoldDisabled.userId,
                updateContent = { content -> content.copy(members = content.members - legalHoldDisabled.userId) },
                createNewContent = { MessageContent.LegalHold.DisabledForMembers(members = listOf(legalHoldDisabled.userId)) }
            )
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

    private suspend fun isUserUnderLegalHold(userId: UserId): Boolean =
        observeLegalHoldStateForUser(userId).firstOrNull() == LegalHoldState.Enabled

    private suspend inline fun <reified T : MessageContent.LegalHold> getLastLegalHoldMessagesForConversations(
        userId: UserId,
        conversations: List<Conversation>,
    ) =
        if (userId == selfUserId) Either.Right(emptyMap()) // for self user we always create new messages
        else messageRepository.getLastMessagesForConversationIds(conversations.map { it.id })
            .map { it.filterValues { it.content is T }.mapValues { it.value.id to (it.value.content as T) } }

    private suspend inline fun <reified T : MessageContent.LegalHold> handleSystemMessages(
        userId: UserId,
        updateContent: (T) -> T,
        createNewContent: () -> T,
    ) {
        // get all conversations where the given user is a member
        conversationRepository.getConversationsByUserId(userId).map { conversations ->
            // get last legal hold messages for the given conversations
            getLastLegalHoldMessagesForConversations<T>(userId, conversations).map { lastMessagesMap ->
                conversations.forEach { conversation ->
                    // create or update legal hold message for members
                    lastMessagesMap[conversation.id]?.let { (lastMessageId, lastMessageContent) ->
                        messageRepository.updateLegalHoldMessage(lastMessageId, conversation.id, updateContent(lastMessageContent))
                    } ?: persistMessage(createSystemMessage(createNewContent(), conversation.id))

                    // create legal hold message for conversation if needed
                    membersHavingLegalHoldClient(conversation.id)
                        .map { if (it.isEmpty()) Conversation.LegalHoldStatus.DISABLED else Conversation.LegalHoldStatus.ENABLED }
                        .map { newConversationLegalHoldStatus ->
                            // if conversation legal hold status has changed, update it
                            if (newConversationLegalHoldStatus != conversation.legalHoldStatus)
                                conversationRepository.updateLegalHoldStatus(conversation.id, newConversationLegalHoldStatus)
                            // if conversation is no longer under legal hold, create system message for it
                            if (newConversationLegalHoldStatus == Conversation.LegalHoldStatus.DISABLED)
                                createSystemMessage(MessageContent.LegalHold.DisabledForConversation, conversation.id)
                        }
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

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
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.getOrNull
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
            it.forEach { conversation ->
                createOrReplaceMemberSystemMessageForConversation(
                    legalHoldEnabled.userId,
                    conversation.id,
                    { content -> content.copy(members = content.members - selfUserId) },
                    { MessageContent.LegalHold.DisabledForMembers(members = listOf(selfUserId)) }
                )

            }
        }

        /*
            TODO:
                - check if this conversation has already been under legal hold
                - if yes, do nothing
                - if not, update conversation
         */

        return Either.Right(Unit)
    }

    override suspend fun handleDisable(legalHoldDisabled: Event.User.LegalHoldDisabled): Either<CoreFailure, Unit> {
        kaliumLogger.i("legal hold disabled for user ${legalHoldDisabled.userId.toLogString()}")
        processEvent(selfUserId, legalHoldDisabled.userId)

        conversationRepository.getConversationsByUserId(legalHoldDisabled.userId).map {
            it.forEach { conversation ->
                createOrReplaceMemberSystemMessageForConversation(
                    legalHoldDisabled.userId,
                    conversation.id,
                    { content -> content.copy(members = content.members - selfUserId) },
                    { MessageContent.LegalHold.DisabledForMembers(members = listOf(selfUserId)) }
                )
            }
        }

        /*
            TODO:
                - check if any other member of this conversation is still under legal hold
                - if yes, do nothing
                - if not, update conversation and create new system message legal hold disabled for conversation
         */

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

    private suspend inline fun <reified T: MessageContent.LegalHold> createOrReplaceMemberSystemMessageForConversation(
        userId: UserId,
        conversationId: ConversationId,
        updateContent: (T) -> T,
        createNewContent: () -> T,
    ) {
        val lastLegalHoldMessageIdAndContent = tryToGetLastMessage<T>(userId, conversationId)
        if (lastLegalHoldMessageIdAndContent != null) {
            val (lastLegalHoldMessageId, lastLegalHoldMessageContent) = lastLegalHoldMessageIdAndContent
            messageRepository.deleteMessage(lastLegalHoldMessageId, conversationId)
                .flatMap { persistMessage(createSystemMessage(updateContent(lastLegalHoldMessageContent), conversationId)) }
        } else persistMessage(createSystemMessage(createNewContent(), conversationId))
    }

    private suspend inline fun <reified T> tryToGetLastMessage(userId: UserId, conversationId: ConversationId): Pair<String, T>? =
        if (userId != selfUserId)
            messageRepository.getLastMessageForConversationId(conversationId)
                .getOrNull()?.let { if (it.content is T) it.id to it.content as T else null }
        else null

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

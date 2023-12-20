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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.client.FetchSelfClientsFromRemoteUseCase
import com.wire.kalium.logic.feature.client.PersistOtherUserClientsUseCase
import com.wire.kalium.logic.feature.legalhold.LegalHoldState
import com.wire.kalium.logic.feature.legalhold.MembersHavingLegalHoldClientUseCase
import com.wire.kalium.logic.feature.legalhold.ObserveLegalHoldStateForUserUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import com.wire.kalium.logic.functional.getOrNull
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.receiver.conversation.message.MessageUnpackResult
import com.wire.kalium.logic.util.DebounceBuffer
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal interface LegalHoldHandler {
    suspend fun handleEnable(legalHoldEnabled: Event.User.LegalHoldEnabled): Either<CoreFailure, Unit>
    suspend fun handleDisable(legalHoldDisabled: Event.User.LegalHoldDisabled): Either<CoreFailure, Unit>
    suspend fun handleNewMessage(message: MessageUnpackResult.ApplicationMessage): Either<CoreFailure, Unit>
}

@Suppress("LongParameterList")
internal class LegalHoldHandlerImpl internal constructor(
    private val selfUserId: UserId,
    private val persistOtherUserClients: PersistOtherUserClientsUseCase,
    private val fetchSelfClientsFromRemote: FetchSelfClientsFromRemoteUseCase,
    private val observeLegalHoldStateForUser: ObserveLegalHoldStateForUserUseCase,
    private val membersHavingLegalHoldClient: MembersHavingLegalHoldClientUseCase,
    private val userConfigRepository: UserConfigRepository,
    private val conversationRepository: ConversationRepository,
    private val legalHoldSystemMessagesHandler: LegalHoldSystemMessagesHandler,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl,
    debounceBufferCapacity: Int = DEBOUNCE_BUFFER_CAPACITY,
    devounceBufferTimeout: Duration = DEBOUNCE_BUFFER_TIMEOUT,
) : LegalHoldHandler {
    private val scope = CoroutineScope(kaliumDispatcher.default)
    private val conversationsWithUpdatedLegalHoldStatus =
        DebounceBuffer<ConversationId>(debounceBufferCapacity, devounceBufferTimeout, scope)

    init {
        scope.launch {
            conversationsWithUpdatedLegalHoldStatus.observe()
                .collect { handleUpdatedBufferedConversations(it) }
        }
    }

    override suspend fun handleEnable(legalHoldEnabled: Event.User.LegalHoldEnabled): Either<CoreFailure, Unit> {
        kaliumLogger.i("legal hold enabled for user ${legalHoldEnabled.userId.toLogString()}")
        // check if the user has already been under legal hold prior to this event
        val userHasBeenUnderLegalHold = isUserUnderLegalHold(legalHoldEnabled.userId)
        // fetch and persist current clients for the given user
        processEvent(selfUserId, legalHoldEnabled.userId)
        // create system messages and notify only if legal hold status has changed for the given user
        if (!userHasBeenUnderLegalHold) {
            if (selfUserId == legalHoldEnabled.userId) { // notify only for self user
                userConfigRepository.setLegalHoldChangeNotified(false)
            }
            handleConversationsForUser(legalHoldEnabled.userId)
            legalHoldSystemMessagesHandler.handleEnabledForUser(legalHoldEnabled.userId)
        }

        return Either.Right(Unit)
    }

    override suspend fun handleDisable(legalHoldDisabled: Event.User.LegalHoldDisabled): Either<CoreFailure, Unit> {
        kaliumLogger.i("legal hold disabled for user ${legalHoldDisabled.userId.toLogString()}")
        // check if the user has already been under legal hold prior to this event
        val userHasBeenUnderLegalHold = isUserUnderLegalHold(legalHoldDisabled.userId)
        // fetch and persist current clients for the given user
        processEvent(selfUserId, legalHoldDisabled.userId)
        // create system messages and notify only if legal hold status has changed for the given user
        if (userHasBeenUnderLegalHold) {
            if (selfUserId == legalHoldDisabled.userId) { // notify only for self user
                userConfigRepository.setLegalHoldChangeNotified(false)
            }
            handleConversationsForUser(legalHoldDisabled.userId)
            legalHoldSystemMessagesHandler.handleDisabledForUser(legalHoldDisabled.userId)
        }

        return Either.Right(Unit)
    }

    override suspend fun handleNewMessage(message: MessageUnpackResult.ApplicationMessage): Either<CoreFailure, Unit> {
        val isStatusChangedForConversation = when (message.content.legalHoldStatus) {
            Conversation.LegalHoldStatus.ENABLED -> handleForConversation(message.conversationId, Conversation.LegalHoldStatus.ENABLED)
            Conversation.LegalHoldStatus.DISABLED -> handleForConversation(message.conversationId, Conversation.LegalHoldStatus.DISABLED)
            else -> false
        }
        if (isStatusChangedForConversation) {
            conversationsWithUpdatedLegalHoldStatus.add(message.conversationId)
        }
        return Either.Right(Unit)
    }

    private suspend fun processEvent(selfUserId: UserId, userId: UserId) {
        if (selfUserId == userId) {
            userConfigRepository.deleteLegalHoldRequest()
            fetchSelfClientsFromRemote()
        } else {
            persistOtherUserClients(userId)
        }
    }

    private suspend fun isUserUnderLegalHold(userId: UserId): Boolean =
        observeLegalHoldStateForUser(userId).firstOrNull() == LegalHoldState.Enabled

    private suspend fun handleForConversation(conversationId: ConversationId, newStatus: Conversation.LegalHoldStatus): Boolean {
        val currentStatus = conversationRepository.observeLegalHoldForConversation(conversationId).firstOrNull()?.getOrNull()
        val isChanged = currentStatus != newStatus
        if (isChanged && newStatus != Conversation.LegalHoldStatus.UNKNOWN) {
            // if conversation legal hold status has changed, update it and create system message for it
            conversationRepository.updateLegalHoldStatus(conversationId, newStatus)
            when (newStatus) {
                Conversation.LegalHoldStatus.DISABLED ->
                    legalHoldSystemMessagesHandler.handleDisabledForConversation(conversationId)
                Conversation.LegalHoldStatus.ENABLED ->
                    legalHoldSystemMessagesHandler.handleEnabledForConversation(conversationId)
                else -> { /* do nothing */ }
            }
        }
        return isChanged
    }

    private suspend fun handleConversationsForUser(userId: UserId) {
        conversationRepository.getConversationsByUserId(userId).map { conversations ->
            conversations.forEach { conversation ->
                // create system message for conversation if needed
                membersHavingLegalHoldClient(conversation.id)
                    .map { if (it.isEmpty()) Conversation.LegalHoldStatus.DISABLED else Conversation.LegalHoldStatus.ENABLED }
                    .map { newLegalHoldStatus -> handleForConversation(conversation.id, newLegalHoldStatus) }
            }
        }
    }

    private suspend fun handleUpdatedBufferedConversations(conversationIds: List<ConversationId>) {
        conversationIds
            .foldToEitherWhileRight(mapOf<UserId, Boolean>()) { conversationId, acc ->
                conversationRepository.getConversationMembers(conversationId)
                    .flatMap { members ->
                        membersHavingLegalHoldClient(conversationId)
                            .map { membersHavingLegalHoldClient ->
                                (acc + members.map { it to membersHavingLegalHoldClient.contains(it) })
                            }
                    }
            }
            .map {
                it.forEach { (userId, userHasBeenUnderLegalHold) ->
                    // TODO: to be optimized - send empty message and handle legal hold discovery after sending a message
                    processEvent(selfUserId, userId)
                    val userIsNowUnderLegalHold = isUserUnderLegalHold(userId)
                    if (userHasBeenUnderLegalHold != userIsNowUnderLegalHold) {
                        if (selfUserId == userId) { // notify only for self user
                            userConfigRepository.setLegalHoldChangeNotified(false)
                        }
                        if (userIsNowUnderLegalHold) legalHoldSystemMessagesHandler.handleEnabledForUser(userId)
                        else legalHoldSystemMessagesHandler.handleDisabledForUser(userId)
                    }
                }
            }
    }

    companion object {
        private const val DEBOUNCE_BUFFER_CAPACITY = 100
        private val DEBOUNCE_BUFFER_TIMEOUT = 3.seconds
    }
}

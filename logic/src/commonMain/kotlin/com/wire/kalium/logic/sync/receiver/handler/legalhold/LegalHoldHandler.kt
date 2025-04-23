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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.client.FetchSelfClientsFromRemoteUseCase
import com.wire.kalium.logic.feature.client.FetchUsersClientsFromRemoteUseCase
import com.wire.kalium.logic.feature.legalhold.LegalHoldState
import com.wire.kalium.logic.feature.legalhold.MembersHavingLegalHoldClientUseCase
import com.wire.kalium.logic.feature.legalhold.ObserveLegalHoldStateForUserUseCase
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.sync.ObserveSyncStateUseCase
import com.wire.kalium.logic.sync.receiver.conversation.message.MessageUnpackResult
import com.wire.kalium.logic.util.TriggerBuffer
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import io.mockative.Mockable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.milliseconds

@Mockable
internal interface LegalHoldHandler {
    suspend fun handleEnable(legalHoldEnabled: Event.User.LegalHoldEnabled): Either<CoreFailure, Unit>
    suspend fun handleDisable(legalHoldDisabled: Event.User.LegalHoldDisabled): Either<CoreFailure, Unit>
    suspend fun handleNewConnection(event: Event.User.NewConnection): Either<CoreFailure, Unit>
    suspend fun handleNewMessage(message: MessageUnpackResult.ApplicationMessage, isLive: Boolean): Either<CoreFailure, Unit>
    suspend fun handleMessageSendFailure(
        conversationId: ConversationId,
        messageInstant: Instant,
        handleFailure: suspend () -> Either<CoreFailure, Unit>
    ): Either<CoreFailure, Boolean>

    suspend fun handleConversationMembersChanged(conversationId: ConversationId): Either<CoreFailure, Unit>
    suspend fun handleUserFetch(userId: UserId, userIsUnderLegalHold: Boolean): Either<CoreFailure, Unit>
}

@Suppress("LongParameterList")
internal class LegalHoldHandlerImpl internal constructor(
    private val selfUserId: UserId,
    private val fetchUsersClientsFromRemote: FetchUsersClientsFromRemoteUseCase,
    private val fetchSelfClientsFromRemote: FetchSelfClientsFromRemoteUseCase,
    private val observeLegalHoldStateForUser: ObserveLegalHoldStateForUserUseCase,
    private val membersHavingLegalHoldClient: MembersHavingLegalHoldClientUseCase,
    private val userConfigRepository: UserConfigRepository,
    private val conversationRepository: ConversationRepository,
    private val legalHoldSystemMessagesHandler: LegalHoldSystemMessagesHandler,
    observeSyncState: ObserveSyncStateUseCase,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl,
) : LegalHoldHandler {
    private val scope = CoroutineScope(kaliumDispatcher.default)

    // FIXME: Sync Cyclic Dependency.
    //        Many functions of this Handler can be called DURING sync. It should not observe sync.
    //        If this is intentional, the `scope.launch` and `bufferedUpdatedConversationIds` should live
    //        In a different class, so we have an entity responsible for scheduling work, and another for performing it.
    private val bufferedUpdatedConversationIds =
        TriggerBuffer<ConversationId>(observeSyncState().distinctUntilChanged().map { it == SyncState.Live }, scope)

    init {
        scope.launch {
            bufferedUpdatedConversationIds.observe()
                .collect { handleUpdatedConversations(it) }
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
            legalHoldSystemMessagesHandler.handleEnabledForUser(legalHoldEnabled.userId, Clock.System.now())
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
            legalHoldSystemMessagesHandler.handleDisabledForUser(legalHoldDisabled.userId, Clock.System.now())
        }

        return Either.Right(Unit)
    }

    override suspend fun handleNewConnection(event: Event.User.NewConnection): Either<CoreFailure, Unit> {
        val connection = event.connection
        when (connection.status) {
            ConnectionState.MISSING_LEGALHOLD_CONSENT -> {
                kaliumLogger.i("missing legal hold consent for connection with user ${connection.qualifiedToId.toLogString()}")
                conversationRepository.updateLegalHoldStatus(connection.qualifiedConversationId, Conversation.LegalHoldStatus.DEGRADED)
            }

            ConnectionState.ACCEPTED -> {
                isUserUnderLegalHold(connection.qualifiedToId).let { isUnderLegalHold ->
                    kaliumLogger.i(
                        "accepted connection with user ${connection.qualifiedToId.toLogString()}" +
                                "who is ${if (isUnderLegalHold) "" else "not"} under legal hold"
                    )
                    val newStatus = if (isUnderLegalHold) Conversation.LegalHoldStatus.ENABLED else Conversation.LegalHoldStatus.DISABLED
                    conversationRepository.updateLegalHoldStatus(connection.qualifiedConversationId, newStatus)
                }
            }

            else -> { /* do nothing */ }
        }
        return Either.Right(Unit)
    }

    override suspend fun handleNewMessage(message: MessageUnpackResult.ApplicationMessage, isLive: Boolean): Either<CoreFailure, Unit> {
        val systemMessageInstant = message.instant - 1.milliseconds
        val isStatusChangedForConversation = when (val legalHoldStatus = message.content.legalHoldStatus) {
            Conversation.LegalHoldStatus.ENABLED, Conversation.LegalHoldStatus.DISABLED ->
                handleForConversation(message.conversationId, legalHoldStatus, systemMessageInstant)

            else -> false
        }
        if (isStatusChangedForConversation) {
            if (isLive) handleUpdatedConversations(listOf(message.conversationId), systemMessageInstant) // handle it right away
            else bufferedUpdatedConversationIds.add(message.conversationId) // buffer and handle after sync
        }
        return Either.Right(Unit)
    }

    override suspend fun handleMessageSendFailure(
        conversationId: ConversationId,
        messageInstant: Instant,
        handleFailure: suspend () -> Either<CoreFailure, Unit>,
    ): Either<CoreFailure, Boolean> =
        membersHavingLegalHoldClient(conversationId).flatMap { membersHavingLegalHoldClientBefore ->
            handleFailure().flatMap {
                val systemMessageInstant = messageInstant - 1.milliseconds
                membersHavingLegalHoldClient(conversationId).map { membersHavingLegalHoldClientAfter ->
                    val newStatus =
                        if (membersHavingLegalHoldClientAfter.isEmpty()) Conversation.LegalHoldStatus.DISABLED
                        else Conversation.LegalHoldStatus.ENABLED
                    val isStatusChangedForConversation = handleForConversation(
                        conversationId = conversationId,
                        newStatus = newStatus,
                        systemMessageInstant = systemMessageInstant
                    )
                    (membersHavingLegalHoldClientBefore - membersHavingLegalHoldClientAfter).forEach {
                        legalHoldSystemMessagesHandler.handleDisabledForUser(it, systemMessageInstant)
                    }
                    (membersHavingLegalHoldClientAfter - membersHavingLegalHoldClientBefore).forEach {
                        legalHoldSystemMessagesHandler.handleEnabledForUser(it, systemMessageInstant)
                    }
                    isStatusChangedForConversation && newStatus == Conversation.LegalHoldStatus.ENABLED
                }
            }
        }

    override suspend fun handleConversationMembersChanged(conversationId: ConversationId): Either<CoreFailure, Unit> =
        membersHavingLegalHoldClient(conversationId)
            .map { if (it.isEmpty()) Conversation.LegalHoldStatus.DISABLED else Conversation.LegalHoldStatus.ENABLED }
            .map { newLegalHoldStatusAfterMembersChange -> handleForConversation(conversationId, newLegalHoldStatusAfterMembersChange) }
            .flatMap {
                conversationRepository.observeIsUserMember(conversationId, selfUserId).first()
                    .flatMap { isSelfUserStillAMember ->
                        if (!isSelfUserStillAMember) {
                            // if self user has left the conversation, set its legal hold status to unknown because from now on this user
                            // won't receive conversation events and updates so it won't be known if the conversation is under legal hold
                            conversationRepository.updateLegalHoldStatus(conversationId, Conversation.LegalHoldStatus.UNKNOWN).map { Unit }
                        } else Either.Right(Unit)
                    }
            }

    private suspend fun processEvent(selfUserId: UserId, userId: UserId) {
        if (selfUserId == userId) {
            userConfigRepository.deleteLegalHoldRequest()
            fetchSelfClientsFromRemote()
        } else {
            fetchUsersClientsFromRemote(listOf(userId))
        }
    }

    override suspend fun handleUserFetch(userId: UserId, userIsUnderLegalHold: Boolean): Either<CoreFailure, Unit> {
        val userHasBeenUnderLegalHold = isUserUnderLegalHold(userId)
        if (userHasBeenUnderLegalHold != userIsUnderLegalHold) {
            processEvent(selfUserId, userId) // fetch and persist current clients for the given user
            handleConversationsForUser(userId)
            if (userIsUnderLegalHold) {
                legalHoldSystemMessagesHandler.handleEnabledForUser(userId, Clock.System.now())
            } else {
                legalHoldSystemMessagesHandler.handleDisabledForUser(userId, Clock.System.now())
            }
        }

        return Either.Right(Unit)
    }

    private suspend fun isUserUnderLegalHold(userId: UserId): Boolean =
        observeLegalHoldStateForUser(userId).firstOrNull() == LegalHoldState.Enabled

    @Suppress("NestedBlockDepth")
    private suspend fun handleForConversation(
        conversationId: ConversationId,
        newStatus: Conversation.LegalHoldStatus,
        systemMessageInstant: Instant = Clock.System.now(),
    ): Boolean =
        if (newStatus != Conversation.LegalHoldStatus.UNKNOWN) {
            conversationRepository.observeLegalHoldStatus(conversationId).firstOrNull()?.getOrNull().let { currentStatus ->
                if (currentStatus != newStatus) {
                    val isChanged = when {
                        currentStatus == Conversation.LegalHoldStatus.DEGRADED && newStatus == Conversation.LegalHoldStatus.ENABLED -> {
                            true // in case it's already degraded we want to keep it, not change it to enabled but create system messages
                        }

                        else -> conversationRepository.updateLegalHoldStatus(conversationId, newStatus).getOrElse(false)
                    }
                    if (isChanged) { // if conversation legal hold status has changed, create system message for it
                        when (newStatus) {
                            Conversation.LegalHoldStatus.DISABLED ->
                                legalHoldSystemMessagesHandler.handleDisabledForConversation(conversationId, systemMessageInstant)

                            Conversation.LegalHoldStatus.ENABLED ->
                                legalHoldSystemMessagesHandler.handleEnabledForConversation(conversationId, systemMessageInstant)

                            else -> { /* do nothing */ }
                        }
                    }
                    isChanged
                } else false
            }
        } else false

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

    private suspend fun handleUpdatedConversations(
        conversationIds: List<ConversationId>,
        systemMessageInstant: Instant = Clock.System.now(),
    ) {
        conversationIds
            .foldToEitherWhileRight(mapOf<UserId, Boolean>()) { conversationId, acc ->
                conversationRepository.getConversationMembers(conversationId)
                    .flatMap { members ->
                        membersHavingLegalHoldClient(conversationId)
                            .map { membersHavingLegalHoldClient ->
                                members.associateWith { membersHavingLegalHoldClient.contains(it) } + acc
                            }
                    }
            }
            .map {
                fetchUsersClientsFromRemote(it.keys.toList())
                it.forEach { (userId, userHasBeenUnderLegalHold) ->
                    val userIsNowUnderLegalHold = isUserUnderLegalHold(userId)
                    if (userHasBeenUnderLegalHold != userIsNowUnderLegalHold) {
                        if (selfUserId == userId) { // notify and delete request only for self user
                            userConfigRepository.setLegalHoldChangeNotified(false)
                            userConfigRepository.deleteLegalHoldRequest()
                        }
                        if (userIsNowUnderLegalHold) legalHoldSystemMessagesHandler.handleEnabledForUser(userId, systemMessageInstant)
                        else legalHoldSystemMessagesHandler.handleDisabledForUser(userId, systemMessageInstant)
                    }
                }
            }
    }
}

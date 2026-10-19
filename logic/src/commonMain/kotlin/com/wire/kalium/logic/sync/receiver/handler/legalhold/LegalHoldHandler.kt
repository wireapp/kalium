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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.client.LegalHoldClients
import com.wire.kalium.logic.feature.client.FetchUsersClientsFromRemoteUseCase
import com.wire.kalium.logic.feature.legalhold.LegalHoldState
import com.wire.kalium.logic.feature.legalhold.MembersHavingLegalHoldClientUseCase
import com.wire.kalium.logic.feature.legalhold.ObserveLegalHoldStateForUserUseCase
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.getOrFail
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.sync.ObserveSyncStateUseCase
import com.wire.kalium.logic.sync.receiver.conversation.message.MessageUnpackResult
import com.wire.kalium.logic.util.TriggerBuffer
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import okio.ByteString.Companion.encodeUtf8
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.milliseconds

internal interface LegalHoldHandler {
    suspend fun recoverPendingTransitions()
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

@Suppress("LongParameterList", "TooManyFunctions") // Recovery shares the existing legal hold owner.
internal class LegalHoldHandlerImpl internal constructor(
    private val selfUserId: UserId,
    private val fetchUsersClientsFromRemote: FetchUsersClientsFromRemoteUseCase,
    private val observeLegalHoldStateForUser: ObserveLegalHoldStateForUserUseCase,
    private val membersHavingLegalHoldClient: MembersHavingLegalHoldClientUseCase,
    private val userConfigRepository: UserConfigRepository,
    private val conversationRepository: ConversationRepository,
    private val legalHoldSystemMessagesHandler: LegalHoldSystemMessagesHandler,
    private val transitions: LegalHoldTransitionJournal,
    private val userTransitions: LegalHoldUserTransitionJournal,
    private val legalHoldClients: LegalHoldClients,
    observeSyncState: ObserveSyncStateUseCase,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl,
) : LegalHoldHandler {
    private val transitionMutex = Mutex()
    private val userTransitionMutex = Mutex()

    override suspend fun recoverPendingTransitions() {
        userTransitionMutex.withLock { recoverUserTransitions() }
        recoverConversationTransitions()
    }

    private suspend fun recoverUserTransitions() {
        userTransitions.pending().forEach { applyUserTransition(it) }
    }

    private suspend fun recoverConversationTransitions() = transitionMutex.withLock {
        transitions.pending().forEach { record ->
            when (val conversation = conversationRepository.getConversationById(record.subject)) {
                is Either.Right -> applyConversationTransition(record)
                is Either.Left -> if (conversation.value == StorageFailure.DataNotFound) {
                    transitions.acknowledge(record)
                } else {
                    throw KaliumSyncException("Legal hold recovery conversation read failed", conversation.value)
                }
            }
        }
    }

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

    override suspend fun handleEnable(legalHoldEnabled: Event.User.LegalHoldEnabled): Either<CoreFailure, Unit> =
        userTransitionMutex.withLock {
            recoverUserTransitions()
            handleUserTransition(legalHoldEnabled.id, legalHoldEnabled.userId, true)
        }

    override suspend fun handleDisable(legalHoldDisabled: Event.User.LegalHoldDisabled): Either<CoreFailure, Unit> =
        userTransitionMutex.withLock {
            recoverUserTransitions()
            handleUserTransition(legalHoldDisabled.id, legalHoldDisabled.userId, false)
        }

    private suspend fun handleUserTransition(eventId: String, userId: UserId, enabled: Boolean): Either<CoreFailure, Unit> {
        val key = LegalHoldUserTransitionJournal.eventKey(eventId, userId, enabled)
        val record = userTransitions.begin(key) {
            val changed = legalHoldClients.isEnabled(userId) != enabled
            val targets = if (changed) conversationRepository.getConversationsByUserId(userId).getOrFail {
                throw KaliumSyncException("Legal hold user conversations read failed", it)
            }.map { it.id } else emptyList()
            LegalHoldUserTransitionJournal.Transition(
                key,
                userId,
                enabled,
                changed,
                Clock.System.now(),
                targets,
                noticeId = "legalhold-user:" + key.encodeUtf8().sha256().hex()
            )
        }
        applyUserTransition(record)
        return Either.Right(Unit)
    }

    @Suppress("ThrowsCount") // Required failures must retain the durable user transition.
    private suspend fun applyUserTransition(pending: LegalHoldUserTransitionJournal.Transition) {
        var record = pending
        if (record.conversationPlan == null) {
            legalHoldClients.refresh(record.user)
            val plan = if (record.noticeNeeded) record.conversations.filter { conversationExists(it) }.map { id ->
                val members = membersHavingLegalHoldClient(id).getOrFail {
                    throw KaliumSyncException("Legal hold members read failed", it)
                }
                LegalHoldUserTransitionJournal.ConversationPlan(id, members.isNotEmpty())
            } else emptyList()
            record = userTransitions.replace(record, record.copy(conversationPlan = plan))
        }
        if (record.user == selfUserId) userConfigRepository.deleteLegalHoldRequest().getOrFail {
            throw KaliumSyncException("Legal hold request cleanup failed", it)
        }
        if (record.noticeNeeded) {
            if (record.user == selfUserId) userConfigRepository.setLegalHoldChangeNotified(false).getOrFail {
                throw KaliumSyncException("Legal hold notification state failed", it)
            }
            val targets = checkNotNull(record.conversationPlan).filter { conversationExists(it.id) }
            for (target in targets) {
                handleForConversation(
                    target.id,
                    if (target.enabled) Conversation.LegalHoldStatus.ENABLED else Conversation.LegalHoldStatus.DISABLED,
                    record.timestamp,
                    "conversation:${record.noticeId}:${target.enabled}"
                )
            }
            if (record.notices == null) {
                val notices = legalHoldSystemMessagesHandler.prepareUserTransition(
                    record.user,
                    record.enabled,
                    targets.map { it.id },
                    record.noticeId
                )
                record = userTransitions.replace(record, record.copy(notices = notices))
            }
            legalHoldSystemMessagesHandler.handleUserTransition(
                record.user,
                record.enabled,
                checkNotNull(record.notices).filter { conversationExists(it.conversation) },
                record.timestamp,
                record.noticeId
            )
        }
        userTransitions.acknowledge(record)
    }

    private suspend fun conversationExists(id: ConversationId): Boolean =
        when (val result = conversationRepository.getConversationById(id)) {
            is Either.Right -> true
            is Either.Left -> if (result.value == StorageFailure.DataNotFound) false else {
                throw KaliumSyncException("Legal hold user conversation read failed", result.value)
            }
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

    override suspend fun handleUserFetch(userId: UserId, userIsUnderLegalHold: Boolean): Either<CoreFailure, Unit> =
        userTransitionMutex.withLock {
            recoverUserTransitions()
            if (legalHoldClients.isEnabled(userId) != userIsUnderLegalHold) {
                handleUserTransition("profile:${kotlin.uuid.Uuid.random()}", userId, userIsUnderLegalHold)
            } else Either.Right(Unit)
        }

    private suspend fun isUserUnderLegalHold(userId: UserId): Boolean =
        observeLegalHoldStateForUser(userId).firstOrNull() == LegalHoldState.Enabled

    private suspend fun applyConversationTransition(record: LegalHoldTransitionJournal.Transition, replay: Boolean = true): Boolean {
        val status = if (record.enabled) Conversation.LegalHoldStatus.ENABLED else Conversation.LegalHoldStatus.DISABLED
        val changed = conversationRepository.updateLegalHoldStatus(record.subject, status).getOrFail {
            throw KaliumSyncException("Legal hold state persistence failed", it)
        }
        if (!changed && !replay) {
            transitions.acknowledge(record)
            return false
        }
        if (record.enabled) legalHoldSystemMessagesHandler.handleEnabledForConversation(record.subject, record.timestamp, record.noticeId)
        else legalHoldSystemMessagesHandler.handleDisabledForConversation(record.subject, record.timestamp, record.noticeId)
        transitions.acknowledge(record)
        return true
    }

    @Suppress("ReturnCount") // Preserve explicit required-history and unchanged-state exits.
    private suspend fun handleForConversation(
        conversationId: ConversationId,
        newStatus: Conversation.LegalHoldStatus,
        systemMessageInstant: Instant = Clock.System.now(),
        noticeId: String? = null,
    ): Boolean = transitionMutex.withLock {
        val key = LegalHoldTransitionJournal.conversationKey(conversationId)
        val pending = transitions.find(key)
        if (pending != null) {
            applyConversationTransition(pending)
            if (pending.enabled == (newStatus == Conversation.LegalHoldStatus.ENABLED)) return@withLock true
        }
        if (newStatus == Conversation.LegalHoldStatus.UNKNOWN) return@withLock false
        val current = conversationRepository.observeLegalHoldStatus(conversationId).first().getOrFail {
            throw KaliumSyncException("Legal hold state read failed", it)
        }
        if (current == newStatus) return@withLock false
        if (current == Conversation.LegalHoldStatus.DEGRADED && newStatus == Conversation.LegalHoldStatus.ENABLED) {
            // This warning does not mutate status. Keep its existing policy until callers
            // supply durable event identities; startup replay must not add another warning.
            legalHoldSystemMessagesHandler.handleEnabledForConversation(conversationId, systemMessageInstant, noticeId)
            return@withLock true
        }
        val record = transitions.begin(key) {
            LegalHoldTransitionJournal.Transition(
                key,
                conversationId,
                newStatus == Conversation.LegalHoldStatus.ENABLED,
                systemMessageInstant,
                noticeId = noticeId ?: kotlin.uuid.Uuid.random().toString()
            )
        }
        applyConversationTransition(record, replay = false)
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

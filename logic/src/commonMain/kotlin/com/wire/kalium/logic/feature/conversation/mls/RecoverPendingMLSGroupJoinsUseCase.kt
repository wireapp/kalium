/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.conversation.mls

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.normalizeFederatedBackendConflict
import com.wire.kalium.common.error.wrapMLSRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.wrapInMLSContext
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.MLSJoinMode
import com.wire.kalium.logic.data.conversation.mls.PendingActionsRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.sync.SyncStateObserver
import com.wire.kalium.logic.sync.receiving.isConversationUnavailable
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.persistence.dao.pendingaction.PendingActionEntity
import kotlinx.coroutines.CancellationException

internal interface RecoverPendingMLSGroupJoinsUseCase {
    suspend operator fun invoke()
}

/** One foreground/session drain. The database survives cancellation; the signal only starts another bounded attempt. */
@Suppress("LongParameterList")
internal class RecoverPendingMLSGroupJoinsUseCaseImpl(
    private val pendingActionsRepository: PendingActionsRepository,
    private val syncStateObserver: SyncStateObserver,
    private val transactionProvider: CryptoTransactionProvider,
    private val joinExistingMLSConversation: JoinExistingMLSConversationUseCase,
    private val conversationRepository: ConversationRepository,
    private val selfUserId: UserId,
    private val requestMore: () -> Unit = {},
) : RecoverPendingMLSGroupJoinsUseCase {

    @Suppress("TooGenericExceptionCaught") // Isolate unexpected failures to one persisted request.
    override suspend fun invoke() {
        if (syncStateObserver.waitUntilLiveOrFailure() is Either.Left) return
        val moreToDiscover = discoverPendingConversations()
        val entries = (
            pendingActionsRepository.getPendingMLSGroupJoinEntries().map { QueuedJoin(it, exactRecovery = true) } +
                pendingActionsRepository.getPendingMLSGroupJoinEntries(exactRecovery = false).map {
                    QueuedJoin(it, exactRecovery = false)
                }
            ).sortedBy { it.entry.createdAt }
        var progressed = false
        for (work in entries.take(BATCH_SIZE)) {
            val result = try {
                transactionProvider.transaction("recoverPendingMLSGroupJoin") { context ->
                    recoverConversation(context, work.entry.qualifiedId.toModel(), work.exactRecovery)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                kaliumLogger.w("Pending MLS join failed before completing its transaction", exception)
                Either.Left(CoreFailure.Unknown(exception))
            }
            progressed = finishAttempt(work.entry, result, work.exactRecovery) || progressed
        }
        // Successful/paused work leaves the front of the queue. Do not spin on transient failures.
        if (progressed && (entries.size > BATCH_SIZE || moreToDiscover)) requestMore()
    }

    private suspend fun recoverConversation(
        context: CryptoTransactionContext,
        id: ConversationId,
        exactRecovery: Boolean,
    ): Either<CoreFailure, Outcome> = joinExistingMLSConversation(
        transactionContext = context,
        conversationId = id,
        mode = if (exactRecovery) MLSJoinMode.RECOVER_EXACT_GROUP else MLSJoinMode.JOIN_OR_ESTABLISH,
    ).flatMap {
        conversationRepository.getNonDeletedConversationById(id)
    }.flatMap { conversation ->
        conversationRepository.getConversationMembers(id).flatMap { members ->
            val protocol = conversation.protocol as? Conversation.ProtocolInfo.MLSCapable
            if (protocol == null || selfUserId !in members || conversation.removedBy != null) {
                Either.Right(Outcome.TERMINAL)
            } else {
                context.wrapInMLSContext { mls ->
                    wrapMLSRequest { mls.conversationExists(protocol.groupId.value) }
                }.flatMap { exists ->
                    Either.Right(if (exists && protocol.groupState == GroupState.ESTABLISHED) Outcome.RECOVERED else Outcome.WAITING)
                }
            }
        }
    }

    private suspend fun finishAttempt(
        entry: PendingActionEntity,
        result: Either<CoreFailure, Outcome>,
        exactRecovery: Boolean,
    ): Boolean = when (result) {
        is Either.Right -> when {
            result.value != Outcome.WAITING -> {
                pendingActionsRepository.acknowledgeMLSGroupJoin(entry, exactRecovery)
                true
            }
            exactRecovery -> {
                pendingActionsRepository.pauseMLSGroupJoin(entry)
                true
            }
            else -> {
                pendingActionsRepository.retryMLSGroupJoin(entry, exactRecovery = false)
                false
            }
        }
        is Either.Left -> {
            val failure = result.value.normalizeFederatedBackendConflict()
            when {
                failure is StorageFailure.DataNotFound -> {
                    pendingActionsRepository.acknowledgeMLSGroupJoin(entry, exactRecovery)
                    true
                }
                !exactRecovery && (
                    failure.isConversationUnavailable() || failure is NetworkFailure.FederatedBackendFailure.ConflictingBackends
                ) -> {
                    pendingActionsRepository.acknowledgeMLSGroupJoin(entry, exactRecovery = false)
                    true
                }
                !exactRecovery || failure.canRetryJoin() -> {
                    pendingActionsRepository.retryMLSGroupJoin(entry, exactRecovery)
                    false
                }
                else -> {
                    // Unavailable groups retain their history. Only membership/deletion events may hide them.
                    // Pausing is version-checked, so newer evidence cannot be hidden by this result.
                    pendingActionsRepository.pauseMLSGroupJoin(entry)
                    true
                }
            }
        }
    }

    private suspend fun discoverPendingConversations(): Boolean {
        val queued = pendingActionsRepository.getPendingMLSGroupJoinEntries()
        val paused = pendingActionsRepository.getPausedMLSGroupJoins()
        val known = (queued + paused).mapTo(mutableSetOf()) { it.qualifiedId.toModel() }
        return transactionProvider.transaction("discoverPendingMLSGroupJoins") { context ->
            var discovered = 0
            for (state in DISCOVERY_STATES) {
                val conversations = conversationRepository.getConversationsByGroupState(state).getOrNull().orEmpty()
                for (conversation in conversations) {
                    if (conversation.id !in known && isMissingPendingGroup(context, conversation)) {
                        pendingActionsRepository.discoverMLSGroupJoin(conversation.id)
                        known += conversation.id
                        discovered++
                        if (discovered == BATCH_SIZE) return@transaction Either.Right(true)
                    }
                }
            }
            clearCompletedPauses(context, paused)
            Either.Right(false)
        }.getOrNull() ?: false
    }

    private suspend fun isMissingPendingGroup(context: CryptoTransactionContext, conversation: Conversation): Boolean {
        val protocol = conversation.protocol as? Conversation.ProtocolInfo.MLSCapable
        if (protocol == null || conversation.removedBy != null) return false
        val members = conversationRepository.getConversationMembers(conversation.id).getOrNull().orEmpty()
        return selfUserId in members && context.wrapInMLSContext { mls ->
            wrapMLSRequest { mls.conversationExists(protocol.groupId.value) }
        }.getOrNull() == false
    }

    private suspend fun clearCompletedPauses(context: CryptoTransactionContext, paused: List<PendingActionEntity>) {
        for (entry in paused) {
            val id: ConversationId = entry.qualifiedId.toModel()
            val conversation = conversationRepository.getNonDeletedConversationById(id)
            if (conversation is Either.Left && conversation.value is StorageFailure.DataNotFound) {
                pendingActionsRepository.clearPausedMLSGroupJoin(entry)
            } else if (conversation is Either.Right) {
                val protocol = conversation.value.protocol as? Conversation.ProtocolInfo.MLSCapable
                if (protocol?.groupState == GroupState.ESTABLISHED &&
                    context.wrapInMLSContext { mls ->
                        wrapMLSRequest { mls.conversationExists(protocol.groupId.value) }
                    }.getOrNull() == true
                ) pendingActionsRepository.clearPausedMLSGroupJoin(entry)
            }
        }
    }

    private fun CoreFailure.canRetryJoin(): Boolean = when (this) {
        CoreFailure.MissingClientRegistration -> true
        is NetworkFailure.NoNetworkConnection -> true
        is NetworkFailure.FederatedBackendFailure.RetryableFailure -> true
        is NetworkFailure.ServerMiscommunication -> {
            val error = kaliumException as? KaliumException.InvalidRequestError
            error == null || error.errorResponse.code == TOO_MANY_REQUESTS || error.errorResponse.code >= SERVER_ERROR
        }
        else -> false
    }

    private data class QueuedJoin(val entry: PendingActionEntity, val exactRecovery: Boolean)

    private enum class Outcome { RECOVERED, TERMINAL, WAITING }

    private companion object {
        const val BATCH_SIZE = 20
        const val TOO_MANY_REQUESTS = 429
        const val SERVER_ERROR = 500
        val DISCOVERY_STATES = listOf(GroupState.PENDING_WELCOME_MESSAGE, GroupState.PENDING_JOIN, GroupState.PENDING_AFTER_RESET)
    }
}

private typealias GroupState = Conversation.ProtocolInfo.MLSCapable.GroupState

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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.wrapMLSRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.distinct
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.wrapInMLSContext
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.ProposalTimer
import com.wire.kalium.logic.data.conversation.SubconversationRepository
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.message.PendingSubconversationProposal
import com.wire.kalium.logic.data.message.PendingSubconversationProposals
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isConversationNotFound
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Schedule pending MLS proposals in a conversation to be committed at a given
 * date. The scheduling persisted and resumes automatically.
 *
 * This is desirable since all clients in an MLS group a collaborating
 * on committing pending proposals, and we want to avoid the scenario of everyone
 * committing pending proposals at same time.
 */
internal interface PendingProposalScheduler {

    /** Start the account-owned collector once the application runtime is started. */
    fun start()

    /**
     * Schedule to commit pending proposals in a given MLS group.
     *
     * @param groupID
     * @param date desired time for when proposals should be committed
     */
    suspend fun scheduleCommit(groupID: GroupID, date: Instant)

}

@Suppress("LongParameterList") // Reuses the account owner and its durable proposal store.
internal class PendingProposalSchedulerImpl(
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val mlsConversationRepository: Lazy<MLSConversationRepository>,
    private val subconversationRepository: Lazy<SubconversationRepository>,
    private val transactionProvider: CryptoTransactionProvider,
    private val subgroupProposals: PendingSubconversationProposals,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl,
    parentContext: CoroutineContext = SupervisorJob(),
) : PendingProposalScheduler {

    /**
     * A dispatcher with limited parallelism of 1.
     * This means using this dispatcher only a single coroutine will be processed at a time.
     */
    private val dispatcher = kaliumDispatcher.default.limitedParallelism(1)
    private val commitPendingProposalsScope = CoroutineScope(parentContext + dispatcher)

    private val started = kotlinx.coroutines.flow.MutableStateFlow(false)

    override fun start() {
        if (!started.compareAndSet(false, true)) return
        commitPendingProposalsScope.launch {
            incrementalSyncRepository.incrementalSyncState.collectLatest { syncState ->
                ensureActive()
                if (syncState == IncrementalSyncStatus.Live) {
                    startCommittingPendingProposals()
                }
            }
        }
    }

    private suspend fun startCommittingPendingProposals() = kotlinx.coroutines.coroutineScope {
        kaliumLogger.d("Start listening for pending proposals to commit")
        timers().cancellable().collect { scheduled ->
            if (scheduled.subgroup == null) consumeScheduled(scheduled)
            else launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { consumeScheduled(scheduled) }
        }
    }

    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth", "LoopWithTooManyJumpStatements", "TooGenericExceptionCaught")
    // Keep retry, native commit and conditional ACK in one cancellation-aware loop.
    private suspend fun consumeScheduled(scheduled: ScheduledProposal) {
        val record = scheduled.subgroup
        while (true) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            try {
                if (record != null && !subgroupProposals.isCurrent(record)) break
                val result = transactionProvider.transaction("PendingProposalScheduler") { transactionContext ->
                    // Recheck after waiting for the account's native transaction owner.
                    if (record != null && !subgroupProposals.isCurrent(record)) return@transaction Either.Right(Unit)
                    transactionContext.wrapInMLSContext { mlsContext ->
                        if (record?.leaveRequested == true) {
                            // Leave intent is durable before wipe. Only confirmed native absence retires it;
                            // a rolled-back wipe or a rejoined group must still follow the normal commit path.
                            wrapMLSRequest { mlsContext.conversationExists(scheduled.timer.groupID.toCrypto()) }
                                .flatMap { exists ->
                                    if (exists) {
                                        mlsConversationRepository.value.commitPendingProposals(mlsContext, scheduled.timer.groupID)
                                    } else Either.Right(Unit)
                                }
                        } else {
                            mlsConversationRepository.value.commitPendingProposals(mlsContext, scheduled.timer.groupID)
                        }
                    }
                }
                if (result is Either.Right) {
                    // This callback is outside the native transaction: commit succeeded.
                    record?.let { subgroupProposals.acknowledge(it) }
                    break
                }
                val failure = (result as Either.Left).value
                if (failure.isUnrecoverableProposalFailure() &&
                    !(record != null && failure == MLSFailure.ConversationNotFound)
                ) {
                    if (record != null) subgroupProposals.acknowledge(record)
                    else mlsConversationRepository.value.clearProposalTimer(scheduled.timer.groupID)
                    break
                }
                if (record == null) break // Preserve the parent conversation scheduler policy.
                kaliumLogger.w("MLS_SUBGROUP_PROPOSAL stage=retry")
                delay(5.seconds)
            } catch (failure: Exception) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (record == null) throw failure
                // SQL/transport exceptions retain the record and do not kill its consumer.
                kaliumLogger.w("MLS_SUBGROUP_PROPOSAL stage=retry")
                delay(5.seconds)
            }
        }
    }

    private data class ScheduledProposal(val timer: ProposalTimer, val subgroup: PendingSubconversationProposal? = null)

    private suspend fun timers() = channelFlow {
        merge(
            mlsConversationRepository.value.observeProposalTimers().map { ScheduledProposal(it) },
            subgroupProposals.observe().flatMapConcat { records -> records.asFlow() }
                .map { ScheduledProposal(ProposalTimer(it.group, it.deadline), it) },
        )
            .distinct()
            .cancellable()
            .collect { scheduled ->
                val timer = scheduled.timer
                ensureActive()
                launch {
                    val secondsUntilFiring = timer.timestamp.minus(DateTimeUtil.currentInstant())
                    if (secondsUntilFiring.inWholeSeconds > 0) {
                        delay(secondsUntilFiring)
                        ensureActive()
                        send(scheduled)
                    } else {
                        send(scheduled)
                    }
                }
            }
    }

    override suspend fun scheduleCommit(groupID: GroupID, date: Instant) {
        kaliumLogger.d("Scheduling to commit pending proposals in ${groupID.toLogString()} at $date")
        if (subconversationRepository.value.containsSubconversation(groupID)) {
            subgroupProposals.schedule(groupID, date)
            kaliumLogger.d("MLS_SUBGROUP_PROPOSAL stage=persisted")
        } else {
            mlsConversationRepository.value.setProposalTimer(ProposalTimer(groupID, date), inMemory = false)
        }
    }

}

private fun Throwable?.isConversationNotFoundError() =
    this is KaliumException.InvalidRequestError && isConversationNotFound()

private fun CoreFailure.isUnrecoverableProposalFailure() =
    this is MLSFailure.ConversationNotFound ||
        this is MLSFailure.StaleProposal ||
        this is NetworkFailure.ServerMiscommunication && kaliumException.isConversationNotFoundError()

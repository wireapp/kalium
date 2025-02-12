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

import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.ProposalTimer
import com.wire.kalium.logic.data.conversation.SubconversationRepository
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.common.functional.distinct
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

/**
 * Schedule pending MLS proposals in a conversation to be committed at a given
 * date. The scheduling persisted and resumes automatically.
 *
 * This is desirable since all clients in an MLS group a collaborating
 * on committing pending proposals, and we want to avoid the scenario of everyone
 * committing pending proposals at same time.
 */
interface PendingProposalScheduler {

    /**
     * Schedule to commit pending proposals in a given MLS group.
     *
     * @param groupID
     * @param date desired time for when proposals should be committed
     */
    suspend fun scheduleCommit(groupID: GroupID, date: Instant)

}

internal class PendingProposalSchedulerImpl(
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val mlsConversationRepository: Lazy<MLSConversationRepository>,
    private val subconversationRepository: Lazy<SubconversationRepository>,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : PendingProposalScheduler {

    /**
     * A dispatcher with limited parallelism of 1.
     * This means using this dispatcher only a single coroutine will be processed at a time.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = kaliumDispatcher.default.limitedParallelism(1)
    private val commitPendingProposalsScope = CoroutineScope(SupervisorJob() + dispatcher)

    init {
        commitPendingProposalsScope.launch() {
            incrementalSyncRepository.incrementalSyncState.collectLatest { syncState ->
                ensureActive()
                if (syncState == IncrementalSyncStatus.Live) {
                    startCommittingPendingProposals()
                }
            }
        }
    }

    private suspend fun startCommittingPendingProposals() {
        kaliumLogger.d("Start listening for pending proposals to commit")
        timers().cancellable().collect() { groupID ->
            kaliumLogger.d("Committing pending proposals in ${groupID.toLogString()}")
            mlsConversationRepository.value.commitPendingProposals(groupID)
                .onFailure {
                    kaliumLogger.e("Failed to commit pending proposals in ${groupID.toLogString()}: $it")
                }
        }
    }

    private suspend fun timers() = channelFlow {
        mlsConversationRepository.value.observeProposalTimers()
            .distinct()
            .cancellable()
            .collect { timer ->
                ensureActive()
                launch() {
                    val secondsUntilFiring = timer.timestamp.minus(DateTimeUtil.currentInstant())
                    if (secondsUntilFiring.inWholeSeconds > 0) {
                        delay(secondsUntilFiring)
                        ensureActive()
                        send(timer.groupID)
                    } else {
                        send(timer.groupID)
                    }
                }
            }
    }

    override suspend fun scheduleCommit(groupID: GroupID, date: Instant) {
        kaliumLogger.d("Scheduling to commit pending proposals in ${groupID.toLogString()} at $date")
        mlsConversationRepository.value.setProposalTimer(
            ProposalTimer(groupID, date),
            inMemory = subconversationRepository.value.containsSubconversation(groupID)
        )
    }

}

package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.ProposalTimer
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.functional.distinct
import com.wire.kalium.logic.functional.flatten
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger
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
import kotlinx.datetime.Clock
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
    private val kaliumConfigs: KaliumConfigs,
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val mlsConversationRepository: Lazy<MLSConversationRepository>,
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
                if (syncState == IncrementalSyncStatus.Live && kaliumConfigs.isMLSSupportEnabled) {
                    startCommittingPendingProposals()
                }
            }
        }
    }

    private suspend fun startCommittingPendingProposals() {
        kaliumLogger.d("Start listening for pending proposals to commit")
        timers().cancellable().collect() { groupID ->
            kaliumLogger.d("Committing pending proposals in $groupID")
            mlsConversationRepository.value.commitPendingProposals(groupID)
                .onFailure {
                    kaliumLogger.e("Failed to commit pending proposals in $groupID: $it")
                }
        }
    }

    private suspend fun timers() = channelFlow {
        mlsConversationRepository.value.observeProposalTimers()
            .flatten()
            .distinct()
            .cancellable()
            .collect { timer ->
                ensureActive()
                launch() {
                    val secondsUntilFiring = timer.timestamp.minus(Clock.System.now())
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
        kaliumLogger.d("Scheduling to commit pending proposals in $groupID at $date")
        mlsConversationRepository.value.setProposalTimer(ProposalTimer(groupID, date))
    }

}

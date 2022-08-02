package com.wire.kalium.logic.sync

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.sync.SyncRepository
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.event.EventProcessor
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

interface SyncManager {
    fun onSlowSyncComplete()

    /**
     * Suspends the caller until all pending events are processed,
     * and the client has finished processing all pending events.
     *
     * Suitable for operations where the user is required to be online
     * and without any pending events to be processed, for example write operations, like:
     * - Creating a conversation
     * - Sending a connection request
     * - Editing conversation settings, etc.
     */
    suspend fun waitUntilLive()

    suspend fun isSlowSyncOngoing(): Boolean
    suspend fun isSlowSyncCompleted(): Boolean
    fun onSlowSyncFailure(cause: CoreFailure): SyncState
}

@Suppress("LongParameterList") // Can't take them out right now. Maybe we can extract an `EventProcessor` on a future PR
internal class SyncManagerImpl(
    private val userSessionWorkScheduler: UserSessionWorkScheduler,
    private val syncRepository: SyncRepository,
    private val eventProcessor: EventProcessor,
    private val eventGatherer: EventGatherer,
    private val syncCriteriaProvider: SyncCriteriaProvider,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : SyncManager {

    /**
     * A dispatcher with limited parallelism of 1.
     * This means using this dispatcher only a single coroutine will be processed at a time.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val eventProcessingDispatcher = kaliumDispatcher.default.limitedParallelism(1)

    /**
     * A [SupervisorJob] that will serve as parent to Sync work being done.
     * This way, [quickSyncJob] can fail or be cancelled and another can be put in its place.
     */
    private val syncSupervisorJob = SupervisorJob()

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        when (throwable) {
            is CancellationException -> {
                kaliumLogger.withFeatureId(SYNC).i("Sync job was cancelled")
                syncRepository.updateSyncState { SyncState.Waiting }
            }

            is KaliumSyncException -> {
                kaliumLogger.withFeatureId(SYNC).i("SyncException during events processing", throwable)
                syncRepository.updateSyncState { SyncState.Failed(throwable.coreFailureCause) }
            }

            else -> {
                kaliumLogger.withFeatureId(SYNC).i("Sync job failed due to unknown reason", throwable)
                syncRepository.updateSyncState { SyncState.Failed(CoreFailure.Unknown(throwable)) }
            }
        }
    }

    private val syncScope = CoroutineScope(syncSupervisorJob + kaliumDispatcher.default)

    init {
        syncScope.launch {
            syncCriteriaProvider.syncCriteriaFlow().collect {
                if (it is SyncCriteriaResolution.Ready) {
                    // START SYNC
                    kaliumLogger.i("Sync starting as all criteria are met")
                    startSyncIfNotYetStarted()
                } else {
                    // STOP SYNC
                    // TODO: Stop SlowSync
                    kaliumLogger.i("Sync Stopped as criteria are not met: $it")
                    quickSyncJob?.cancel()
                }
            }
        }
    }

    /**
     * The scope in which the processing of events run.
     * All coroutines will have limited parallelism, as this scope uses [eventProcessingDispatcher].
     * All coroutines will have [syncSupervisorJob] as their parent.
     * @see eventProcessingDispatcher
     * @see syncSupervisorJob
     */
    private val quickSyncScope = CoroutineScope(syncSupervisorJob + eventProcessingDispatcher + coroutineExceptionHandler)
    private var quickSyncJob: Job? = null

    override fun onSlowSyncComplete() {
        // Processing already running, don't launch another
        kaliumLogger.withFeatureId(SYNC).d("SyncManager.onSlowSyncComplete called")
        val isRunning = quickSyncJob?.isActive ?: false
        if (isRunning) {
            kaliumLogger.withFeatureId(SYNC).d("SyncManager.processingJob still active. Sync won't keep going")
            return
        }
        quickSyncJob?.cancel(null)

        syncRepository.updateSyncState { SyncState.GatheringPendingEvents }

        quickSyncJob = quickSyncScope.launch {
            gatherAndProcessEvents()
        }
    }

    private suspend fun gatherAndProcessEvents() {
        eventGatherer.gatherEvents().collect {
            eventProcessor.processEvent(it)
        }
        kaliumLogger.withFeatureId(SYNC).i("SYNC Finished gathering and processing events")
        syncRepository.updateSyncState { SyncState.Waiting }
    }

    override fun onSlowSyncFailure(cause: CoreFailure) = syncRepository.updateSyncState { SyncState.Failed(cause) }

    override suspend fun waitUntilLive() {
        syncRepository.syncState.first { it == SyncState.Live }
    }

    private fun startSyncIfNotYetStarted() {
        syncRepository.updateSyncState {
            when (it) {
                SyncState.Waiting, is SyncState.Failed -> {
                    userSessionWorkScheduler.enqueueSlowSyncIfNeeded()
                    SyncState.SlowSync
                }

                else -> it
            }
        }
    }

    override suspend fun isSlowSyncOngoing(): Boolean = syncRepository.syncState.first() == SyncState.SlowSync
    override suspend fun isSlowSyncCompleted(): Boolean =
        syncRepository.syncState.first() in setOf(SyncState.GatheringPendingEvents, SyncState.Live)
}

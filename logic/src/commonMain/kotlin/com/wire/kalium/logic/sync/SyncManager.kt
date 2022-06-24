package com.wire.kalium.logic.sync

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.sync.SyncRepository
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.kaliumLogger
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
     * Triggers sync, if not yet running.
     * Suspends the caller until all pending events are processed,
     * and the client has finished processing all pending events.
     *
     * Suitable for operations where the user is required to be online
     * and without any pending events to be processed, for maximum sync.
     * @see startSyncIfIdle
     * @see waitUntilSlowSyncCompletion
     */
    suspend fun waitUntilLive()

    /**
     * Triggers sync, if not yet running.
     * Suspends the caller until at least basic data is processed,
     * even though Sync will run on a Job of its own.
     *
     * Suitable for operations where the user can be offline, but at least some basic post-login sync is done.
     * @see startSyncIfIdle
     * @see waitUntilLive
     */
    suspend fun waitUntilSlowSyncCompletion()

    /**
     * Triggers sync, if not yet running.
     * Will run in a parallel job without waiting for completion.
     *
     * Suitable for operations that the user can perform even while offline.
     * @see waitUntilLive
     * @see waitUntilSlowSyncCompletion
     */
    fun startSyncIfIdle()
    suspend fun isSlowSyncOngoing(): Boolean
    suspend fun isSlowSyncCompleted(): Boolean
    fun onSlowSyncFailure(cause: CoreFailure): SyncState
}

@Suppress("LongParameterList") //Can't take them out right now. Maybe we can extract an `EventProcessor` on a future PR
internal class SyncManagerImpl(
    private val userSessionWorkScheduler: UserSessionWorkScheduler,
    private val eventRepository: EventRepository,
    private val syncRepository: SyncRepository,
    private val conversationEventReceiver: ConversationEventReceiver,
    private val userEventReceiver: EventReceiver<Event.User>,
    private val eventGatherer: EventGatherer,
    private val kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : SyncManager {

    /**
     * A dispatcher with limited parallelism of 1.
     * This means using this dispatcher only a single coroutine will be processed at a time.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val eventProcessingDispatcher = kaliumDispatcher.default.limitedParallelism(1)

    /**
     * A [SupervisorJob] that will serve as parent to the [processingJob].
     * This way, [processingJob] can fail or be cancelled and another can be put in its place.
     */
    private val processingSupervisorJob = SupervisorJob()

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        when (throwable) {
            is CancellationException -> {
                kaliumLogger.i("Sync job was cancelled")
                syncRepository.updateSyncState { SyncState.Waiting }
            }

            is KaliumSyncException -> {
                kaliumLogger.i("SyncException during events processing", throwable)
                syncRepository.updateSyncState { SyncState.Failed(throwable.coreFailureCause) }
            }

            else -> {
                kaliumLogger.i("Sync job failed due to unknown reason", throwable)
                syncRepository.updateSyncState { SyncState.Failed(CoreFailure.Unknown(throwable)) }
            }
        }
    }

    /**
     * The scope in which the processing of events run.
     * All coroutines will have limited parallelism, as this scope uses [eventProcessingDispatcher].
     * All coroutines will have [processingSupervisorJob] as their parent.
     * @see eventProcessingDispatcher
     * @see processingSupervisorJob
     */
    private val eventProcessingScope = CoroutineScope(processingSupervisorJob + eventProcessingDispatcher + coroutineExceptionHandler)
    private var processingJob: Job? = null

    override fun onSlowSyncComplete() {
        // Processing already running, don't launch another
        kaliumLogger.d("SyncManager.onSlowSyncComplete called")
        val isRunning = processingJob?.isActive ?: false
        if (isRunning) {
            kaliumLogger.d("SyncManager.processingJob still active. Sync won't keep going")
            return
        }
        processingJob?.cancel(null)

        syncRepository.updateSyncState { SyncState.GatheringPendingEvents }

        processingJob = eventProcessingScope.launch { gatherAndProcessEvents() }
    }

    private suspend fun gatherAndProcessEvents() = eventGatherer.gatherEvents().collect {
        processEvent(it)
    }

    private suspend fun processEvent(event: Event) {
        kaliumLogger.i(message = "SYNC: Processing event ${event.id}")
        when (event) {
            is Event.Conversation -> conversationEventReceiver.onEvent(event)
            is Event.User -> userEventReceiver.onEvent(event)
            is Event.Unknown -> kaliumLogger.i("Unhandled event id=${event.id}")
        }
        eventRepository.updateLastProcessedEventId(event.id)
    }

    override fun onSlowSyncFailure(cause: CoreFailure) = syncRepository.updateSyncState { SyncState.Failed(cause) }

    override suspend fun waitUntilLive() {
        startSyncIfIdle()
        syncRepository.syncState.first { it == SyncState.Live }
    }

    override suspend fun waitUntilSlowSyncCompletion() {
        startSyncIfIdle()
        syncRepository.syncState.first { it in setOf(SyncState.GatheringPendingEvents, SyncState.Live) }
    }

    override fun startSyncIfIdle() {
        syncRepository.updateSyncState {
            when (it) {
                SyncState.Waiting, is SyncState.Failed -> {
                    SyncState.SlowSync
                }

                else -> it
            }
        }
        userSessionWorkScheduler.enqueueSlowSyncIfNeeded()
    }

    override suspend fun isSlowSyncOngoing(): Boolean = syncRepository.syncState.first() == SyncState.SlowSync
    override suspend fun isSlowSyncCompleted(): Boolean =
        syncRepository.syncState.first() in setOf(SyncState.GatheringPendingEvents, SyncState.Live)
}

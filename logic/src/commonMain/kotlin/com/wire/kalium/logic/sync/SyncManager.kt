package com.wire.kalium.logic.sync

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.sync.SyncRepository
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

interface SyncManager {
    fun onSlowSyncComplete()

    /**
     * Blocks the caller until all pending events are processed.
     */
    suspend fun waitForSyncToComplete()
    suspend fun isSlowSyncOngoing(): Boolean
    suspend fun isSlowSyncCompleted(): Boolean
    fun onSlowSyncFailure(cause: CoreFailure): SyncState
}

class SyncManagerImpl(
    private val workScheduler: WorkScheduler,
    private val eventRepository: EventRepository,
    kaliumDispatcher: KaliumDispatcher,
    private val syncRepository: SyncRepository,
    private val conversationEventReceiver: EventReceiver<Event.Conversation>
) : SyncManager {

    /**
     * A dispatcher with limited parallelism of 1.
     * This means using this dispatcher only a single coroutine will be processed at a time.
     */
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
                syncRepository.updateSyncState { SyncState.WAITING }
            }
            else -> {
                kaliumLogger.i("Sync job failed due to unknown reason", throwable)
                syncRepository.updateSyncState { SyncState.FAILED }
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
    private val processingScope = CoroutineScope(processingSupervisorJob + eventProcessingDispatcher + coroutineExceptionHandler)
    private var processingJob: Job? = null

    override fun onSlowSyncComplete() {
        syncRepository.updateSyncState { SyncState.PROCESSING_PENDING_EVENTS }

        // Processing already running, don't launch another
        val isRunning = processingJob?.isActive ?: false
        if (isRunning) return

        processingJob = processingScope.launch { processAllEvents() }
    }

    private suspend fun processAllEvents() {
        syncRepository.updateSyncState { SyncState.PROCESSING_PENDING_EVENTS }

        eventRepository.pendingEvents().collect {
            it.onFailure { failure ->
                throw KaliumSyncException("Failure when receiving pending events", failure)
            }.onSuccess { event ->
                processEvent(event)
            }
        }

        syncRepository.updateSyncState { SyncState.LIVE }
        // TODO: Connect to the WS BEFORE fetching pending events to make
        //  sure that no event is lost between last page and WS handshake.
        //  We need to collect and store the events we receive from the WS,
        //  and delete them as we process the pending ones in case they
        //  show up both in the WS and the pending events pages.
        //  The not deleted/remaining ones would be the ones we would
        //  have lost, and we need to process them.
        eventRepository.liveEvents().onSuccess {
            it.catch { throwable ->
                throw KaliumSyncException("Websocket disconnected", NetworkFailure.NoNetworkConnection(throwable))
            }.collect { event ->
                processEvent(event)
            }
            throw KaliumSyncException("Websocket event collecting stopped", NetworkFailure.NoNetworkConnection(null))
        }.onFailure { failure ->
            throw KaliumSyncException("Failure when receiving live events", failure)
        }
    }

    private suspend fun processEvent(event: Event) {
        kaliumLogger.i(message = "Event received: $event")
        when (event) {
            is Event.Conversation -> {
                conversationEventReceiver.onEvent(event)
            }
            else -> {
                kaliumLogger.i(message = "Unhandled event id=${event.id}")
            }
        }
        eventRepository.updateLastProcessedEventId(event.id)
    }

    override fun onSlowSyncFailure(cause: CoreFailure) = syncRepository.updateSyncState { SyncState.FAILED }

    override suspend fun waitForSyncToComplete() {
        performSyncIfWaitingOrFailed()
        syncRepository.syncState.first { it == SyncState.LIVE }
    }

    private fun performSyncIfWaitingOrFailed() {
        val syncState = syncRepository.updateSyncState {
            when (it) {
                SyncState.WAITING, SyncState.FAILED -> SyncState.SLOW_SYNC
                else -> it
            }
        }

        if (syncState == SyncState.SLOW_SYNC) {
            workScheduler.enqueueImmediateWork(SlowSyncWorker::class, SlowSyncWorker.name)
        }
    }

    override suspend fun isSlowSyncOngoing(): Boolean = syncRepository.syncState.first() == SyncState.SLOW_SYNC
    override suspend fun isSlowSyncCompleted(): Boolean =
        syncRepository.syncState.first() in setOf(SyncState.LIVE, SyncState.PROCESSING_PENDING_EVENTS)
}

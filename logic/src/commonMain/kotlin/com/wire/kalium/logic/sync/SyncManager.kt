package com.wire.kalium.logic.sync

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.sync.SyncRepository
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import io.ktor.utils.io.errors.IOException
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

class SyncManagerImpl(
    private val userSessionWorkScheduler: UserSessionWorkScheduler,
    private val eventRepository: EventRepository,
    private val syncRepository: SyncRepository,
    private val conversationEventReceiver: ConversationEventReceiver,
    private val userEventReceiver: EventReceiver<Event.User>,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
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
    private val processingScope = CoroutineScope(processingSupervisorJob + eventProcessingDispatcher + coroutineExceptionHandler)
    private var processingJob: Job? = null

    override fun onSlowSyncComplete() {
        // Processing already running, don't launch another
        val isRunning = processingJob?.isActive ?: false
        if (isRunning) return

        processingJob = processingScope.launch { processAllEvents() }
    }

    private suspend fun processAllEvents() {
        syncRepository.updateSyncState { SyncState.ProcessingPendingEvents }

        processPendingEvents()

        syncRepository.updateSyncState { SyncState.Live }
        // TODO: Connect to the WS BEFORE fetching pending events to make
        //  sure that no event is lost between last page and WS handshake.
        //  We need to collect and store the events we receive from the WS,
        //  and delete them as we process the pending ones in case they
        //  show up both in the WS and the pending events pages.
        //  The not deleted/remaining ones would be the ones we would
        //  have lost, and we need to process them.
        processLiveEvents()
    }

    /**
     * Collects a stream of live events until connection is dropped or client is closed.
     * In a perfect world where connections don't drop, this should never end.
     * @throws KaliumSyncException when processing stops.
     */
    @Suppress("TooGenericExceptionCaught") // We need to catch any exceptions and mark Sync as failed
    private suspend fun processLiveEvents() {
        val processingStopCause = eventRepository.liveEvents().fold({ failure ->
            KaliumSyncException("Failure when receiving live events", failure)
        }, { eventFlow ->
            try {
                eventFlow.collect { event ->
                    processEvent(event)
                }
                KaliumSyncException("Websocket event collecting stopped", NetworkFailure.NoNetworkConnection(null))
            } catch (io: IOException) {
                KaliumSyncException("Websocket disconnected", NetworkFailure.NoNetworkConnection(io))
            } catch (t: Throwable) {
                KaliumSyncException("Unknown Websocket error", CoreFailure.Unknown(t))
            }
        })
        throw processingStopCause
    }

    private suspend fun processPendingEvents() {
        eventRepository.pendingEvents().collect {
            it.onFailure { failure ->
                throw KaliumSyncException("Failure when receiving pending events", failure)
            }.onSuccess { event ->
                processEvent(event)
            }
        }
    }

    private suspend fun processEvent(event: Event) {
        kaliumLogger.i(message = "Event received: $event")
        when (event) {
            is Event.Conversation -> {
                conversationEventReceiver.onEvent(event)
            }
            is Event.User -> {
                userEventReceiver.onEvent(event)
            }
            else -> {
                kaliumLogger.i(message = "Unhandled event id=${event.id}")
            }
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
        syncRepository.syncState.first { it is SyncState.ProcessingPendingEvents || it is SyncState.Live }
    }

    override fun startSyncIfIdle() {
        val syncState = syncRepository.updateSyncState {
            when (it) {
                SyncState.Waiting, is SyncState.Failed -> SyncState.SlowSync
                else -> it
            }
        }

        if (syncState == SyncState.SlowSync) {
            userSessionWorkScheduler.enqueueImmediateWork(SlowSyncWorker::class, SlowSyncWorker.name)
        }
    }

    override suspend fun isSlowSyncOngoing(): Boolean = syncRepository.syncState.first() == SyncState.SlowSync
    override suspend fun isSlowSyncCompleted(): Boolean =
        syncRepository.syncState.first() in setOf(SyncState.Live, SyncState.ProcessingPendingEvents)
}

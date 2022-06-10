package com.wire.kalium.logic.sync

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventMapper
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.sync.SyncRepository
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.network.api.notification.WebSocketEvent
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val workScheduler: WorkScheduler,
    private val eventRepository: EventRepository,
    private val syncRepository: SyncRepository,
    private val conversationEventReceiver: ConversationEventReceiver,
    private val userEventReceiver: EventReceiver<Event.User>,
    private val kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl,
    private val eventMapper: EventMapper = MapperProvider.eventMapper()
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

    // Do not access this variable directly of I will cut off your hands
    private val offlineEventsBuffer = mutableListOf<Event>()

    private var processingEventFlow = MutableSharedFlow<Event>()

    private val mutex = Mutex()

    private suspend fun addToOfflineEventBuffer(event: Event) =
        mutex.withLock {
            offlineEventsBuffer.add(event)
        }

    private suspend fun isEventPresentInOfflineBuffer(event: Event): Boolean =
        mutex.withLock {
            offlineEventsBuffer.contains(event)
        }

    private suspend fun clearOfflineEventBuffer() =
        mutex.withLock {
            offlineEventsBuffer.clear()
        }

    override fun onSlowSyncComplete() {
        // Processing already running, don't launch another
        val isRunning = processingJob?.isActive ?: false
        if (isRunning) return

        syncRepository.updateSyncState { SyncState.GatheringPendingEvents }

        processingJob?.cancel(null)
        processingJob = eventProcessingScope.launch { startProcessing() }
    }

    private suspend fun startProcessing() = eventProcessingScope.launch {
        launch(kaliumDispatcher.io) {
            gatherEvents()
        }

        processingEventFlow.collect {
            processEvent(it)
        }
    }

    private suspend fun gatherEvents() {
        eventRepository.getLastProcessedEventId().onSuccess { eventId ->
            eventRepository.updateLastProcessedEventId(eventId)
            eventRepository.liveEvents()
                .onSuccess { webSocketEventFlow ->
                    webSocketEventFlow.collect { webSocketEvent ->
                        when (webSocketEvent) {
                            is WebSocketEvent.Open -> {
                                kaliumLogger.i("SYNC: Websocket Open")

//                                val delay = Clock.System.now().epochSeconds + 5
//                                while(Clock.System.now().epochSeconds < delay) {
//
//                                }

                                eventRepository
                                    .pendingEvents()
                                    .mapNotNull { offlineEventOrFailure ->
                                        when (offlineEventOrFailure) {
                                            is Either.Left -> null
                                            is Either.Right -> offlineEventOrFailure.value
                                        }
                                    }
                                    .collect {
                                        kaliumLogger.i("SYNC: Collecting offline event: ${it.id}")
                                        addToOfflineEventBuffer(it)
                                        processingEventFlow.emit(it)
                                    }
                                kaliumLogger.i("SYNC: Offline events collection finished")
                                syncRepository.updateSyncState { SyncState.ProcessingLiveEvents }
                            }
                            is WebSocketEvent.BinaryPayloadReceived -> {
                                kaliumLogger.i("SYNC: Websocket Received binary payload")
                                val mappedEvent = eventMapper.fromDTO(webSocketEvent.payload)
                                mappedEvent.forEach {
                                    if (!isEventPresentInOfflineBuffer(it)) {
                                        kaliumLogger.d(
                                            "SYNC: Event never seen before ${it.id} - We are live"
                                        )
                                        processingEventFlow.emit(it)
                                    } else {
                                        kaliumLogger.d(
                                            "SYNC: Skipping emit of event from WebSocket because already emitted as offline event ${it.id}"
                                        )
                                        clearOfflineEventBuffer()
                                    }
                                }
                            }
                            is WebSocketEvent.Close -> {
                                throw when (val cause = webSocketEvent.cause) {
                                    is IOException ->
                                        KaliumSyncException("Websocket disconnected", NetworkFailure.NoNetworkConnection(cause))
                                    is Throwable ->
                                        KaliumSyncException("Unknown Websocket error", CoreFailure.Unknown(cause))
                                    else -> KaliumSyncException(
                                        "Websocket event collecting stopped",
                                        NetworkFailure.NoNetworkConnection(null)
                                    )
                                }
                            }
                            is WebSocketEvent.NonBinaryPayloadReceived -> {
                                kaliumLogger.w(
                                    "Non binary event received on Websocket"
                                )
                            }
                        }
                    }
                }
        }
    }

    private suspend fun processEvent(event: Event) {
        kaliumLogger.i(message = "SYNC: Processing event ${event.id}")
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
        syncRepository.syncState.first { it == SyncState.ProcessingLiveEvents }
    }

    override suspend fun waitUntilSlowSyncCompletion() {
        startSyncIfIdle()
        syncRepository.syncState.first { it in setOf(SyncState.GatheringPendingEvents, SyncState.ProcessingLiveEvents) }
    }

    override fun startSyncIfIdle() {
        val syncState = syncRepository.updateSyncState {
            when (it) {
                SyncState.Waiting, is SyncState.Failed -> SyncState.SlowSync
                else -> it
            }
        }

        if (syncState == SyncState.SlowSync) {
            workScheduler.enqueueImmediateWork(SlowSyncWorker::class, SlowSyncWorker.name)
        }
    }

    override suspend fun isSlowSyncOngoing(): Boolean = syncRepository.syncState.first() == SyncState.SlowSync
    override suspend fun isSlowSyncCompleted(): Boolean =
        syncRepository.syncState.first() in setOf(SyncState.GatheringPendingEvents, SyncState.ProcessingLiveEvents)
}

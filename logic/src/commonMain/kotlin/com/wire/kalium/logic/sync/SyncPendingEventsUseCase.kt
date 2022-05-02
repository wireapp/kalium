package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion

class SyncPendingEventsUseCase(
    private val syncManager: SyncManager,
    private val eventRepository: EventRepository,
    private val conversationEventReceiver: EventReceiver<Event.Conversation>
) {

    /**
     * @return Flow<Unit> that emits only once - when syncing Pending Events was done
     */
    suspend operator fun invoke(): Flow<Unit> {
        syncManager.waitForSlowSyncToComplete()

        return flow {
            eventRepository.pendingEvents()
                .onCompletion { emit(Unit) }
                .collect { either ->
                    suspending {
                        either.map { event ->
                            kaliumLogger.i(message = "Event received: $event")
                            when (event) {
                                is Event.Conversation -> conversationEventReceiver.onEvent(event)
                                else -> kaliumLogger.i(message = "Unhandled event id=${event.id}")
                            }
                            eventRepository.updateLastProcessedEventId(event.id)
                        }
                    }.onFailure {
                        kaliumLogger.e(message = "Failure when receiving events: $it")
                    }
                }
        }
    }
}

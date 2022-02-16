package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure

class ListenToEventsUseCase(
    private val syncManager: SyncManager,
    private val eventRepository: EventRepository
) {

    /**
     * TODO: Make this thing a singleton. So we can't create multiple websockets/event processing instances
     */
    suspend operator fun invoke() {
        syncManager.waitForSlowSyncToComplete()
        eventRepository.events()
            //TODO: Handle retry/reconnection
            .collect { either ->
            either.map { event ->
                println("Event received: $event")
                when (event) {
                    is Event.Conversation.NewMessage -> {
                        //TODO Handle messages!
                    }
                    else -> {
                        //TODO: Multiplatform logging
                        println("Unhandled event id=${event.id}")
                    }
                }
            }.onFailure {
                //TODO: Multiplatform logging
                println("Failure when receiving events: $it")
            }
        }
    }

}

package com.wire.kalium.logic.sync

import com.wire.kalium.logger.Logger
import com.wire.kalium.logger.LoggerType
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.suspending

class ListenToEventsUseCase(
    private val syncManager: SyncManager,
    private val eventRepository: EventRepository,
    private val conversationEventReceiver: EventReceiver<Event.Conversation>
) {

    /**
     * TODO: Make this thing a singleton. So we can't create multiple websockets/event processing instances
     */
    suspend operator fun invoke() {
        syncManager.waitForSlowSyncToComplete()

        val logger = Logger(
            tag = "ListenToEventsUseCase",
            type = LoggerType.DEBUG
        )

        eventRepository.events()
            //TODO: Handle retry/reconnection
            .collect { either ->
                suspending {
                    either.map { event ->
                        //TODO: Multiplatform logging
                        logger.log(message = "Event received: $event")
                        println("Event received: $event")
                        when (event) {
                            is Event.Conversation -> {
                                conversationEventReceiver.onEvent(event)
                            }
                            else -> {
                                //TODO: Multiplatform logging
                                println("Unhandled event id=${event.id}")
                            }
                        }
                    }
                }.onFailure {
                    //TODO: Multiplatform logging
                    println("Failure when receiving events: $it")
                }
            }
    }

}

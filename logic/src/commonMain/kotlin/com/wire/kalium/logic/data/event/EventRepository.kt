package com.wire.kalium.logic.data.event

import com.wire.kalium.network.api.notification.Event
import com.wire.kalium.persistence.event.EventInfoStorage
import kotlinx.coroutines.flow.Flow

interface EventRepository {
    suspend fun events(): Flow<Event>
}

class EventDataSource(
    private val eventInfoStorage: EventInfoStorage,
) : EventRepository {

    override suspend fun events(): Flow<Event> {
        TODO("Not yet implemented")
    }
}

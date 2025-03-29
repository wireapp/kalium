/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.kalium.logic.sync.local

import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventDeliveryInfo
import com.wire.kalium.logic.data.event.EventEnvelope
import com.wire.kalium.logic.sync.incremental.EventSource
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * LocalEventRepository stores local events that are produced by API responses but are not received through EventRepository.
 * It provides a flow that LocalEventManager can listen for processing these events.
 */
internal interface LocalEventRepository {
    /**
     * Emits a new local event.
     */
    fun emitLocalEvent(event: Event)

    /**
     * Returns a shared flow of local events.
     */
    fun observeLocalEvents(): Flow<EventEnvelope>
}

internal class LocalEventRepositoryImpl : LocalEventRepository {

    private val localEventFlow = MutableSharedFlow<EventEnvelope>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override fun emitLocalEvent(event: Event) {
        val eventEnvelope = EventEnvelope(event, EventDeliveryInfo(isTransient = true, EventSource.LIVE))
        localEventFlow.tryEmit(eventEnvelope)
    }

    override fun observeLocalEvents(): Flow<EventEnvelope> = localEventFlow.asSharedFlow()
}

package com.wire.kalium

import com.wire.kalium.models.backend.Payload
import java.util.UUID

interface IEventProcessor {
    fun processEvent(eventId: UUID, payload: Payload, wireClient: IWireClient)
}

package com.wire.kalium

import com.wire.kalium.models.backend.Payload
import java.util.*

interface IEventProcessor {
    fun processEvent(eventId: UUID, payload: Payload, wireClient: IWireClient)
}

package com.wire.kalium

import com.wire.kalium.backend.models.Payload
import java.util.*

interface IEventProcessor {
    fun processEvent(eventId: UUID, payload: Payload, client: IWireClient)
}

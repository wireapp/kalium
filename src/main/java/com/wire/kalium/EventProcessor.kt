package com.wire.kalium

import com.wire.kalium.backend.models.Payload
import java.util.*

interface EventProcessor {
    fun processEvent(eventId: UUID, payload: Payload, client: WireClient)
}

package com.wire.kalium

import com.wire.kalium.backend.models.Payload
import java.util.*

interface EventHandler {
    fun handleEvent(eventId: UUID, payload: Payload, client: WireClient)
}

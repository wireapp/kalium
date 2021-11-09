package com.wire.kalium

import com.wire.kalium.backend.models.Payload
import java.util.*

interface EventHandler {
    fun handleMessage(eventId: UUID, payload: Payload, client: WireClient)
}

package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.event.Event

fun interface EventReceiver<T : Event> {
    suspend fun onEvent(event: T)
}

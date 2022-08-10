package com.wire.kalium.logic.sync.incremental

/**
 * Informs where an event came from.
 */
enum class EventSource {
    /**
     * Event happened while this client was offline.
     */
    PENDING,

    /**
     * Event received in real-time, in an active
     * connection with Wire servers.
     */
    LIVE
}

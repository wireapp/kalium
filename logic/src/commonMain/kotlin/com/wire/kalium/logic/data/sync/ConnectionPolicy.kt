package com.wire.kalium.logic.data.sync

/**
 * Represents how CoreLogic should handle the connection
 * to the backend when performing Sync and receiving events.
 * @see SyncState
 */
enum class ConnectionPolicy {
    /**
     * After gathering and processing pending events,
     * a websocket connection will be kept alive,
     * receiving live events.
     *
     *
     * This is the default and most usually needed behaviour
     * when the client app is on the foreground, and it's expected
     * for the application to keep consuming resources.
     *
     * @see DISCONNECT_AFTER_PENDING_EVENTS
     */
    KEEP_ALIVE,

    /**
     * After gathering and processing pending events,
     * the websocket should be dropped, impeding the client
     * from staying online and receiving live events.
     *
     * ### Use-cases
     *
     * Needed, for example, when the application wakes
     * up when receiving a push notification, and wants
     * to free resources after processing notifications.
     *
     * @see KEEP_ALIVE
     */
    DISCONNECT_AFTER_PENDING_EVENTS
}

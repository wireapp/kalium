package com.wire.kalium.logic.data.sync

enum class SyncState {

    /**
     * Sync hasn't started yet.
     */
    WAITING,

    /**
     * Fetching all initial data:
     * - Self user profile
     * - All devices from self user
     * - All conversations
     * - All connection requests and statuses
     * - Team (if user belongs to a team)
     * - All team members (if user belongs to a team)
     * - Details of all other users discovered in past steps
     */
    SLOW_SYNC,

    /**
     * Fetches all events lost since last time this client
     * was [LIVE].
     * This implies that [SLOW_SYNC] is done.
     */
    PROCESSING_PENDING_EVENTS,

    /**
     * Is connected to the server and receiving real-time events.
     * This implies that [PROCESSING_PENDING_EVENTS] is done.
     */
    LIVE,

    //TODO Specify reason for sync failure.
    // Maybe convert to a Sealed Class and make Failure hold a cause.
    /**
     * Sync was not completed due to a failure.
     */
    FAILED,
}

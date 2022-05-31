package com.wire.kalium.logic.data.sync

import com.wire.kalium.logic.CoreFailure

sealed class SyncState {

    /**
     * Sync hasn't started yet.
     */
    object Waiting : SyncState()

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
    object SlowSync : SyncState()

    /**
     * Fetches all events lost since last time this client
     * was [Live].
     * This implies that [SlowSync] is done.
     */
    object ProcessingPendingEvents : SyncState()

    /**
     * Is connected to the server and receiving real-time events.
     * This implies that [ProcessingPendingEvents] is done.
     */
    object Live : SyncState()

    /**
     * Sync was not completed due to a failure.
     */
    data class Failed(val cause: CoreFailure) : SyncState()
}

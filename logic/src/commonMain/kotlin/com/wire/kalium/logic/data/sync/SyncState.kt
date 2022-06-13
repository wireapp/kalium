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
     * Is fetching events lost while this client was offline.
     * Implies that [SlowSync] is done.
     */
    object GatheringPendingEvents : SyncState()

    /**
     * Is processing events, connected to the server and receiving real-time events.
     * This implies that [GatheringPendingEvents] is done.
     */
    object Live : SyncState()

    /**
     * Sync was not completed due to a failure.
     */
    data class Failed(val cause: CoreFailure) : SyncState()
}

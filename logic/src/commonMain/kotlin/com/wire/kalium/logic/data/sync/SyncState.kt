/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.data.sync

import com.wire.kalium.logic.CoreFailure

sealed class SyncState {

    /**
     * Sync hasn't started yet.
     */
    data object Waiting : SyncState()

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
    data object SlowSync : SyncState()

    /**
     * Is fetching events lost while this client was offline.
     * Implies that [SlowSync] is done.
     */
    data object GatheringPendingEvents : SyncState()

    /**
     * Is processing events, connected to the server and receiving real-time events.
     * This implies that [GatheringPendingEvents] is done.
     */
    data object Live : SyncState()

    /**
     * Sync was not completed due to a failure.
     */
    data class Failed(val cause: CoreFailure) : SyncState()
}

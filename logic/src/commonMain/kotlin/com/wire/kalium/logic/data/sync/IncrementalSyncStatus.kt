/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

sealed interface IncrementalSyncStatus {

    object Pending : IncrementalSyncStatus {
        override fun toString(): String = "PENDING"
    }

    object FetchingPendingEvents : IncrementalSyncStatus {
        override fun toString() = "FETCHING_PENDING_EVENTS"
    }

    object Live : IncrementalSyncStatus {
        override fun toString() = "LIVE"
    }

    data class Failed(val failure: CoreFailure) : IncrementalSyncStatus {
        override fun toString() = "FAILED, cause: '$failure'"
    }

}

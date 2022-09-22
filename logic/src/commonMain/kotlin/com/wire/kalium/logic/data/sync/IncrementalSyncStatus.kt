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

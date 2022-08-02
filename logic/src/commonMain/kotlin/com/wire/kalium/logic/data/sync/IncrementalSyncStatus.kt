package com.wire.kalium.logic.data.sync

import com.wire.kalium.logic.CoreFailure

sealed interface IncrementalSyncStatus {

    object Pending : IncrementalSyncStatus

    object FetchingPendingEvents : IncrementalSyncStatus

    data class Complete(val outcome: IncrementalSyncOutcome) : IncrementalSyncStatus

    data class Failed(val failure: CoreFailure) : IncrementalSyncStatus

}

enum class IncrementalSyncOutcome {
    LIVE, DISCONNECTED_DUE_TO_POLICY
}

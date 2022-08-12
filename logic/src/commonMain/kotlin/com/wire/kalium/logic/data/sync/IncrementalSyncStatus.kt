package com.wire.kalium.logic.data.sync

import com.wire.kalium.logic.CoreFailure

sealed interface IncrementalSyncStatus {

    object Pending : IncrementalSyncStatus

    object FetchingPendingEvents : IncrementalSyncStatus

    object Live : IncrementalSyncStatus

    data class Failed(val failure: CoreFailure) : IncrementalSyncStatus

}

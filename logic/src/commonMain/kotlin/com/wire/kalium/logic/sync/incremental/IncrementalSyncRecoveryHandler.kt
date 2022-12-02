package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.network.exceptions.KaliumException

internal class IncrementalSyncRecoveryHandler(
    private val slowSyncRepository: SlowSyncRepository,
    private val incrementalSyncRepository: IncrementalSyncRepository
) {
    suspend fun recover(failure: CoreFailure, onRetry: suspend () -> Unit) {
        kaliumLogger.i("$TAG Checking if we can recover from the failure: $failure")
        incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Failed(failure))
        if (shouldRestartSlowSyncProcess(failure)) {
            kaliumLogger.i("$TAG Cannot recover from the failure: $failure restarting slow sync")
            slowSyncRepository.clearLastSlowSyncCompletionInstant()
        } else {
            kaliumLogger.i("$TAG Retrying to recover form the failure $failure, perform the incremental sync again")
            onRetry()
        }
    }

    private fun shouldRestartSlowSyncProcess(failure: CoreFailure): Boolean {
        return isClientOrEventNotFound(failure)
    }

    private fun isClientOrEventNotFound(failure: CoreFailure): Boolean =
        (failure is NetworkFailure.ServerMiscommunication) &&
                (failure.kaliumException is KaliumException.InvalidRequestError) &&
                (failure.kaliumException.errorResponse.code == 404)

    private companion object {
        private const val TAG = "IncrementalSyncRecoveryHandler"
    }
}

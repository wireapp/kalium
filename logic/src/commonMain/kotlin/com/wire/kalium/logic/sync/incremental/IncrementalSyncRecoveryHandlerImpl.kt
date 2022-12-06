package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.network.exceptions.KaliumException

internal interface IncrementalSyncRecoveryHandler {
    suspend fun recover(failure: CoreFailure, onRetryCallback: OnRetryCallback)
}

internal interface OnRetryCallback {
    suspend fun retry()
}

internal class IncrementalSyncRecoveryHandlerImpl(
    private val slowSyncRepository: SlowSyncRepository
) : IncrementalSyncRecoveryHandler {

    override suspend fun recover(failure: CoreFailure, onRetryCallback: OnRetryCallback) {
        kaliumLogger.i("$TAG Checking if we can recover from the failure: $failure")
        if (shouldRestartSlowSyncProcess(failure)) {
            slowSyncRepository.clearLastSlowSyncCompletionInstant()
        } else {
            kaliumLogger.i("$TAG Retrying to recover form the failure $failure, perform the incremental sync again")
            onRetryCallback.retry()
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

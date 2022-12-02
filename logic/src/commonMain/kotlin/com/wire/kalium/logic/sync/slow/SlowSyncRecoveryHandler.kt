package com.wire.kalium.logic.sync.slow

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.failure.SelfUserDeleted
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.kaliumLogger

class SlowSyncRecoveryHandler(private val logoutUseCase: LogoutUseCase) {

    suspend fun recover(failure: CoreFailure, onRetry: suspend () -> Unit) {
        kaliumLogger.i("$TAG Checking if we can recover from the failure: $failure")
        when (failure) {
            is SelfUserDeleted -> {
                kaliumLogger.i("$TAG Self user has been deleted, cannot recover, performing logout: $failure")
                logoutUseCase(LogoutReason.DELETED_ACCOUNT)
            }

            else -> {
                kaliumLogger.i("$TAG Retrying to recover form the failure $failure, performing the slow sync again")
                onRetry()
            }
        }
    }

    private companion object {
        const val TAG = "SlowSyncRecoveryHandler"
    }

}

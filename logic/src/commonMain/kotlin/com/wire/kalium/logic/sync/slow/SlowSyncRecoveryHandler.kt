package com.wire.kalium.logic.sync.slow

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.failure.SelfUserDeleted
import com.wire.kalium.logic.feature.auth.LogoutUseCase

class SlowSyncRecoveryHandler(private val logoutUseCase: LogoutUseCase) {

    suspend fun recover(failure: CoreFailure, onRetry: suspend () -> Unit) {
        when (failure) {
            is SelfUserDeleted -> logoutUseCase(LogoutReason.DELETED_ACCOUNT)
            else -> onRetry()
        }
    }

}

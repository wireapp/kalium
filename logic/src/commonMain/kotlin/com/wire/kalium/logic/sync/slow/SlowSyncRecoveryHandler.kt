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

package com.wire.kalium.logic.sync.slow

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.failure.SelfUserDeleted
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.kaliumLogger

internal interface SlowSyncRecoveryHandler {
    suspend fun recover(failure: CoreFailure, onSlowSyncRetryCallback: OnSlowSyncRetryCallback)
}

internal fun interface OnSlowSyncRetryCallback {
    suspend fun retry()
}

internal class SlowSyncRecoveryHandlerImpl(private val logoutUseCase: LogoutUseCase) : SlowSyncRecoveryHandler {

    override suspend fun recover(failure: CoreFailure, onSlowSyncRetryCallback: OnSlowSyncRetryCallback) {
        kaliumLogger.i("$TAG Checking if we can recover from the failure: $failure")
        when (failure) {
            is SelfUserDeleted -> {
                kaliumLogger.i("$TAG Self user has been deleted, cannot recover, performing logout: $failure")
                logoutUseCase(LogoutReason.DELETED_ACCOUNT)
            }

            else -> {
                kaliumLogger.i("$TAG Retrying to recover form the failure $failure, performing the slow sync again")
                onSlowSyncRetryCallback.retry()
            }
        }
    }

    private companion object {
        const val TAG = "SlowSyncRecoveryHandler"
    }
}

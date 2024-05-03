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

package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.user.AccountRepository
import com.wire.kalium.logic.feature.auth.ValidateUserHandleResult
import com.wire.kalium.logic.feature.auth.ValidateUserHandleUseCase
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isHandleExists
import com.wire.kalium.network.exceptions.isInvalidHandle

sealed class SetUserHandleResult {
    data object Success : SetUserHandleResult()
    sealed class Failure : SetUserHandleResult() {
        data object InvalidHandle : Failure()
        data object HandleExists : Failure()
        data class Generic(val error: CoreFailure) : Failure()
    }
}

/**
 * Sets the user's handle remotely and locally.
 */
class SetUserHandleUseCase internal constructor(
    private val accountRepository: AccountRepository,
    private val validateUserHandle: ValidateUserHandleUseCase,
    private val syncManager: SyncManager
) {
    /**
     * @param handle the handle to set for the user
     * @return the [SetUserHandleResult.Success] if successful, otherwise a mapped failure.
     */
    suspend operator fun invoke(handle: String): SetUserHandleResult {
        if (syncManager.isSlowSyncOngoing()) {
            syncManager.waitUntilLive()
        }
        return validateUserHandle(handle).let { handleState ->
            when (handleState) {
                is ValidateUserHandleResult.Valid -> accountRepository.updateSelfHandle(handleState.handle).fold(
                    {
                        if (it is NetworkFailure.ServerMiscommunication && it.kaliumException is KaliumException.InvalidRequestError)
                            handleSpecificError(it.kaliumException)
                        else SetUserHandleResult.Failure.Generic(it)
                    }, {
                        if (syncManager.isSlowSyncCompleted()) accountRepository.updateLocalSelfUserHandle(handleState.handle)
                        SetUserHandleResult.Success
                    }
                )

                else -> SetUserHandleResult.Failure.InvalidHandle
            }
        }
    }

    private fun handleSpecificError(error: KaliumException.InvalidRequestError): SetUserHandleResult.Failure = with(error) {
        when {
            isInvalidHandle() -> SetUserHandleResult.Failure.InvalidHandle
            isHandleExists() -> SetUserHandleResult.Failure.HandleExists
            else -> SetUserHandleResult.Failure.Generic(NetworkFailure.ServerMiscommunication(this))
        }
    }
}

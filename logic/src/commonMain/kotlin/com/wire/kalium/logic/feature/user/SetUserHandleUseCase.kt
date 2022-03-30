package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.auth.ValidateUserHandleUseCase
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isHandleExists
import com.wire.kalium.network.exceptions.isInvalidHandle


sealed class SetUserHandleResult {
    object Success : SetUserHandleResult()
    sealed class Failure : SetUserHandleResult() {
        object InvalidHandle : Failure()
        object HandleExists : Failure()
        data class Generic(val error: CoreFailure) : Failure()
    }
}

class SetUserHandleUseCase(
    private val userRepository: UserRepository,
    private val validateUserHandleUseCase: ValidateUserHandleUseCase,
    private val syncManager: SyncManager
) {
    suspend operator fun invoke(handle: String): SetUserHandleResult = suspending {
        when (validateUserHandleUseCase(handle)) {
            true -> userRepository.updateSelfHandle(handle).coFold(
                {
                    if (it is NetworkFailure.ServerMiscommunication && it.kaliumException is KaliumException.InvalidRequestError) {
                        handleSpecificError(it.kaliumException)
                    } else {
                        SetUserHandleResult.Failure.Generic(it)
                    }
                }, {
                    if (syncManager.isSlowSyncOngoing())
                        syncManager.waitForSlowSyncToComplete()
                    if (syncManager.isSlowSyncCompleted())
                        userRepository.updateLocalSelfUserHandle(handle)
                    SetUserHandleResult.Success
                }
            )
            false -> SetUserHandleResult.Failure.InvalidHandle
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

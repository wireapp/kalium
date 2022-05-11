package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.auth.ValidateUserHandleResult
import com.wire.kalium.logic.feature.auth.ValidateUserHandleUseCase
import com.wire.kalium.logic.functional.fold
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
    private val validateUserHandle: ValidateUserHandleUseCase,
    private val syncManager: SyncManager
) {
    suspend operator fun invoke(_handle: String): SetUserHandleResult {
        if (syncManager.isSlowSyncOngoing()) {
            syncManager.waitForSlowSyncToComplete()
        }
        return validateUserHandle(_handle).let { handleState ->
            when (handleState) {
                is ValidateUserHandleResult.Valid -> userRepository.updateSelfHandle(handleState.handle).fold(
                    {
                        if (it is NetworkFailure.ServerMiscommunication && it.kaliumException is KaliumException.InvalidRequestError)
                            handleSpecificError(it.kaliumException)
                        else SetUserHandleResult.Failure.Generic(it)
                    }, {
                        if (syncManager.isSlowSyncCompleted()) userRepository.updateLocalSelfUserHandle(handleState.handle)
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

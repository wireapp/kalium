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

package com.wire.kalium.logic.feature.client

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.DeleteClientParam
import com.wire.kalium.logic.feature.user.UpdateSupportedProtocolsAndResolveOneOnOnesUseCase
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isBadRequest
import com.wire.kalium.network.exceptions.isInvalidCredentials
import com.wire.kalium.network.exceptions.isMissingAuth

/**
 * This use case is responsible for deleting the client.
 * The client will be deleted from the backend and the local storage.
 */
interface DeleteClientUseCase {
    suspend operator fun invoke(param: DeleteClientParam): DeleteClientResult
}

internal class DeleteClientUseCaseImpl(
    private val clientRepository: ClientRepository,
    private val updateSupportedProtocolsAndResolveOneOnOnes: UpdateSupportedProtocolsAndResolveOneOnOnesUseCase,
) : DeleteClientUseCase {
    override suspend operator fun invoke(param: DeleteClientParam): DeleteClientResult =
        clientRepository.deleteClient(param)
            .onSuccess {
                updateSupportedProtocolsAndResolveOneOnOnes(
                    synchroniseUsers = true
                )
            }
            .fold(
            {
                handleError(it)
            }, {
                DeleteClientResult.Success
            })

    private fun handleError(failure: NetworkFailure): DeleteClientResult.Failure =
        if (failure is NetworkFailure.ServerMiscommunication && failure.kaliumException is KaliumException.InvalidRequestError) {
            val error = failure.kaliumException as KaliumException.InvalidRequestError
            when {
                error.isInvalidCredentials() -> DeleteClientResult.Failure.InvalidCredentials
                error.isMissingAuth() -> DeleteClientResult.Failure.PasswordAuthRequired
                error.isBadRequest() -> DeleteClientResult.Failure.InvalidCredentials
                else -> DeleteClientResult.Failure.Generic(failure)
            }
        } else {
            DeleteClientResult.Failure.Generic(failure)
        }
}

sealed class DeleteClientResult {
    data object Success : DeleteClientResult()

    sealed class Failure : DeleteClientResult() {
        data object InvalidCredentials : Failure()
        data object PasswordAuthRequired : Failure()
        data class Generic(val genericFailure: CoreFailure) : Failure()
    }
}

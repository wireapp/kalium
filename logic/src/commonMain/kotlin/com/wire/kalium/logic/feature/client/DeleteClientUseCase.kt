/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.DeleteClientParam
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.feature.user.UpdateSupportedProtocolsUseCase
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.onSuccess
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
    private val updateSupportedProtocols: UpdateSupportedProtocolsUseCase,
    private val userRepository: UserRepository,
    private val oneOnOneResolver: OneOnOneResolver
) : DeleteClientUseCase {
    override suspend operator fun invoke(param: DeleteClientParam): DeleteClientResult =
        clientRepository.deleteClient(param)
            .onSuccess {
                updateSupportedProtocols().onSuccess { updated ->
                    if (updated) {
                        userRepository.fetchAllOtherUsers().flatMap {
                            oneOnOneResolver.resolveAllOneOnOneConversations()
                        }
                    }
                }
            }
            .fold(
            {
                handleError(it)
            }, {
                DeleteClientResult.Success
            })

    private fun handleError(failure: NetworkFailure): DeleteClientResult.Failure =
        if (failure is NetworkFailure.ServerMiscommunication && failure.kaliumException is KaliumException.InvalidRequestError)
            when {
                failure.kaliumException.isInvalidCredentials() -> DeleteClientResult.Failure.InvalidCredentials
                failure.kaliumException.isMissingAuth() -> DeleteClientResult.Failure.PasswordAuthRequired
                failure.kaliumException.isBadRequest() -> DeleteClientResult.Failure.InvalidCredentials
                else -> DeleteClientResult.Failure.Generic(failure)
            }
        else {
            DeleteClientResult.Failure.Generic(failure)
        }
}

sealed class DeleteClientResult {
    object Success : DeleteClientResult()

    sealed class Failure : DeleteClientResult() {
        object InvalidCredentials : Failure()
        object PasswordAuthRequired : Failure()
        data class Generic(val genericFailure: CoreFailure) : Failure()
    }
}

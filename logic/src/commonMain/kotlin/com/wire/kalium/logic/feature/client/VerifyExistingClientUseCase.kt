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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.DelicateKaliumApi

/**
 * Checks if the given client is still exists on the backend, otherwise returns failure.
 */
interface VerifyExistingClientUseCase {

    /**
     * @param clientId client id of client
     * @return success if the given id is registered, failure otherwise
     */
    suspend operator fun invoke(clientId: ClientId): VerifyExistingClientResult
}

internal class VerifyExistingClientUseCaseImpl @OptIn(DelicateKaliumApi::class) constructor(
    private val selfUserId: UserId,
    private val clientRepository: ClientRepository,
    private val isAllowedToRegisterMLSClient: IsAllowedToRegisterMLSClientUseCase,
    private val registerMLSClientUseCase: RegisterMLSClientUseCase,
) : VerifyExistingClientUseCase {

    @OptIn(DelicateKaliumApi::class)
    override suspend fun invoke(clientId: ClientId): VerifyExistingClientResult {
        return clientRepository.selfListOfClients()
            .fold({
                VerifyExistingClientResult.Failure.Generic(it)
            }, { listOfRegisteredClients ->
                val registeredClient = listOfRegisteredClients.firstOrNull { it.id == clientId }
                when {
                    registeredClient == null -> VerifyExistingClientResult.Failure.ClientNotRegistered

                    !registeredClient.isMLSCapable && isAllowedToRegisterMLSClient() -> {
                        registerMLSClientUseCase.invoke(clientId = registeredClient.id).fold({
                            VerifyExistingClientResult.Failure.Generic(it)
                        }, {
                            if (it is RegisterMLSClientResult.E2EICertificateRequired)
                                VerifyExistingClientResult.Failure.E2EICertificateRequired(registeredClient, selfUserId)
                            else VerifyExistingClientResult.Success(registeredClient)
                        })
                    }

                    else -> VerifyExistingClientResult.Success(registeredClient)
                }
            })
    }
}

sealed class VerifyExistingClientResult {
    data class Success(val client: Client) : VerifyExistingClientResult()

    sealed class Failure : VerifyExistingClientResult() {
        data object ClientNotRegistered : Failure()
        data class Generic(val genericFailure: CoreFailure) : Failure()
        class E2EICertificateRequired(val client: Client, val userId: UserId) : Failure()
    }
}

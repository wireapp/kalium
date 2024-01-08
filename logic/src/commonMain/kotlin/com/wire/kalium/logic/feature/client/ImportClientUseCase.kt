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

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.functional.fold

interface ImportClientUseCase {

    /**
     * @param clientId client id of client
     * @param registerClientParam: register client parameters for the case when client isn't already registered
     * @return success if the client was successfully imported, failure otherwise
     */
    suspend operator fun invoke(clientId: ClientId, registerClientParam: RegisterClientUseCase.RegisterClientParam): RegisterClientResult
}

internal class ImportClientUseCaseImpl(
    private val clientRepository: ClientRepository,
    private val getOrRegisterClient: GetOrRegisterClientUseCase
) : ImportClientUseCase {

    override suspend fun invoke(
        clientId: ClientId,
        registerClientParam: RegisterClientUseCase.RegisterClientParam
    ): RegisterClientResult {
        return clientRepository.persistRetainedClientId(clientId)
            .fold(
                { coreFailure ->
                    RegisterClientResult.Failure.Generic(coreFailure)
                },
                {
                    getOrRegisterClient(registerClientParam)
                }
            )
    }
}

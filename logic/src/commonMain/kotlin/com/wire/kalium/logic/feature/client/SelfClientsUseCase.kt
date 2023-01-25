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
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.getOrNull

/**
 * This use case will return the list of clients of the current user.
 */
interface SelfClientsUseCase {
    /**
     * @return the [SelfClientsResult] with the list of clients of the current user, otherwise a [CoreFailure]
     */
    suspend operator fun invoke(): SelfClientsResult
}

class SelfClientsUseCaseImpl(
    private val clientRepository: ClientRepository,
    private val provideClientId: CurrentClientIdProvider,
) : SelfClientsUseCase {
    override suspend fun invoke(): SelfClientsResult = clientRepository.selfListOfClients().fold(
        { SelfClientsResult.Failure.Generic(it) },
        { clients ->
            val currentClientId = provideClientId()
            SelfClientsResult.Success(
                clients = clients.sortedByDescending { it.registrationTime },
                currentClientId = currentClientId.getOrNull()
            )
        }
    )
}

sealed class SelfClientsResult {
    data class Success(val clients: List<Client>, val currentClientId: ClientId?) : SelfClientsResult()

    sealed class Failure : SelfClientsResult() {
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}

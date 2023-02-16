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
 * This use case will return the details of a client.
 */
interface GetClientDetailsUseCase {
    /**
     * @param clientId the id of the client to get the details for
     * @return the [GetClientDetailsResult] with the client details, otherwise a [CoreFailure]
     */
    suspend operator fun invoke(clientId: ClientId): GetClientDetailsResult
}

class GetClientDetailsUseCaseImpl(
    private val clientRepository: ClientRepository,
    private val provideClientId: CurrentClientIdProvider,
) : GetClientDetailsUseCase {
    override suspend fun invoke(clientId: ClientId): GetClientDetailsResult = clientRepository.clientInfo(clientId)
        .fold(
            { GetClientDetailsResult.Failure.Generic(it) },
            { client ->
                provideClientId.invoke().getOrNull()?.let { currentClientId ->
                    GetClientDetailsResult.Success(client, currentClientId.value == clientId.value)
                } ?: GetClientDetailsResult.Success(client, false)
            }
        )
}

sealed class GetClientDetailsResult {
    data class Success(val client: Client, val isCurrentClient: Boolean) : GetClientDetailsResult()

    sealed class Failure : GetClientDetailsResult() {
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}

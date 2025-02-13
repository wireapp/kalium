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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.fold

/**
 * Updates the verification status of a client.
 * @param userId The user id of the client.
 * @param clientId The client id of the client.
 * @param verified The new verification status of the client.
 * @return [UpdateClientVerificationStatusUseCase.Result.Success] if the client was updated successfully.
 * [UpdateClientVerificationStatusUseCase.Result.Failure] if the client could not be updated.
 */
class UpdateClientVerificationStatusUseCase internal constructor(
    private val clientRepository: ClientRepository
) {
    suspend operator fun invoke(userId: UserId, clientId: ClientId, verified: Boolean): Result =
        clientRepository.updateClientProteusVerificationStatus(userId, clientId, verified).fold(
            { error -> Result.Failure(error) },
            { Result.Success }
        )

    sealed interface Result {
        data object Success : Result
        data class Failure(val error: StorageFailure) : Result
    }
}

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
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.OtherUserClient
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold

/**
 * Use case to fetch all the other user clients (devices) information from the local db for specific user
 */
interface GetOtherUserClientsUseCase {
    suspend operator fun invoke(userId: UserId): GetOtherUserClientsResult
}

internal class GetOtherUserClientsUseCaseImpl(
    private val clientRepository: ClientRepository
) : GetOtherUserClientsUseCase {
    override suspend operator fun invoke(userId: UserId): GetOtherUserClientsResult =
        clientRepository.getClientsByUserId(userId).fold({
            GetOtherUserClientsResult.Failure.UserNotFound
        }, {
            GetOtherUserClientsResult.Success(it)
        })
}

sealed class GetOtherUserClientsResult {
    class Success(val otherUserClients: List<OtherUserClient>) : GetOtherUserClientsResult()

    sealed class Failure : GetOtherUserClientsResult() {
        object UserNotFound : Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}

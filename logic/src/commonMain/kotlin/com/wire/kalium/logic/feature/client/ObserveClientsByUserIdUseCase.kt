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
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.fold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Use case to fetch all the other user clients (devices) information from the local db for specific user
 */
class ObserveClientsByUserIdUseCase(
    private val clientRepository: ClientRepository
) {
    suspend operator fun invoke(userId: UserId): Flow<Result> =
        clientRepository.observeClientsByUserId(userId).map { clients ->
            clients.fold(Result::Failure, Result::Success)
        }

    sealed class Result {
        class Success(val clients: List<Client>) : Result()
        class Failure(val genericFailure: CoreFailure) : Result()
    }
}

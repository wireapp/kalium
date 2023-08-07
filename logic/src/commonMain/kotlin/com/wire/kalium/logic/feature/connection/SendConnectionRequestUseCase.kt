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

package com.wire.kalium.logic.feature.connection

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger

/**
 * Use Case that allows a user send a connection request to connect with another User
 */
interface SendConnectionRequestUseCase {
    /**
     * Use case [SendConnectionRequestUseCase] operation
     *
     * @param userId the target user to connect with
     * @return a [SendConnectionRequestResult] indicating the operation result
     */
    suspend operator fun invoke(userId: UserId): SendConnectionRequestResult
}

internal class SendConnectionRequestUseCaseImpl(
    private val connectionRepository: ConnectionRepository
) : SendConnectionRequestUseCase {

    override suspend fun invoke(userId: UserId): SendConnectionRequestResult {
        return connectionRepository.sendUserConnection(userId)
            .fold({ coreFailure ->
                kaliumLogger.e("An error occurred when sending a connection request to $userId")
                when (coreFailure) {
                    is NetworkFailure.FederatedBackendFailure.FederationDenied ->
                        SendConnectionRequestResult.Failure.FederationDenied

                    else -> SendConnectionRequestResult.Failure.GenericFailure(coreFailure)
                }
            }, {
                SendConnectionRequestResult.Success
            })
    }
}

sealed class SendConnectionRequestResult {
    data object Success : SendConnectionRequestResult()

    sealed class Failure : SendConnectionRequestResult() {
        class GenericFailure(val coreFailure: CoreFailure) : SendConnectionRequestResult()
        data object FederationDenied : SendConnectionRequestResult()
    }

}

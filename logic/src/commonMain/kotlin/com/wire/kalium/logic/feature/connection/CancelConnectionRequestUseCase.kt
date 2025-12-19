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

package com.wire.kalium.logic.feature.connection

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.client.CryptoTransactionProvider

/**
 * Use Case that allows a user to cancel a [ConnectionState.PENDING] connection request to connect with another user
 * before it's [ConnectionState.ACCEPTED]
 *
 */
public interface CancelConnectionRequestUseCase {
    /**
     * Use case [CancelConnectionRequestUseCase] operation
     *
     * @param userId the target user with whom to cancel the connection request
     * @return a [CancelConnectionRequestUseCaseResult] indicating the operation result
     */
    public suspend operator fun invoke(userId: UserId): CancelConnectionRequestUseCaseResult
}

internal class CancelConnectionRequestUseCaseImpl(
    private val connectionRepository: ConnectionRepository,
    private val transactionProvider: CryptoTransactionProvider,
) : CancelConnectionRequestUseCase {

    override suspend fun invoke(userId: UserId): CancelConnectionRequestUseCaseResult {
        return transactionProvider
            .transaction("CancelConnectionRequest") { transactionContext ->
                connectionRepository.updateConnectionStatus(
                    transactionContext,
                    userId,
                    ConnectionState.CANCELLED
                )
            }
            .fold({
                kaliumLogger.e("An error occurred when cancelling the connection request to $userId")
                CancelConnectionRequestUseCaseResult.Failure(it)
            }, {
                CancelConnectionRequestUseCaseResult.Success
            })
    }
}

public sealed class CancelConnectionRequestUseCaseResult {
    public data object Success : CancelConnectionRequestUseCaseResult()
    public data class Failure(val coreFailure: CoreFailure) : CancelConnectionRequestUseCaseResult()
}

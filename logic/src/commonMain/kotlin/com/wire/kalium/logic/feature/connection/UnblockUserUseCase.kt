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
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger

/**
 * Use Case that allows the current self user to unblock another previously blocked user
 */
fun interface UnblockUserUseCase {
    /**
     * Use case [UnblockUserUseCase] operation
     *
     * @param userId the target user whom to unblock
     * @return a [UnblockUserResult] indicating the operation result
     */
    suspend operator fun invoke(userId: UserId): UnblockUserResult
}

internal class UnblockUserUseCaseImpl(
    private val connectionRepository: ConnectionRepository
) : UnblockUserUseCase {

    override suspend fun invoke(userId: UserId): UnblockUserResult =
        connectionRepository.updateConnectionStatus(userId, ConnectionState.ACCEPTED)
            .fold({
                kaliumLogger.e("An error occurred when unblocking a user $userId")
                UnblockUserResult.Failure(it)
            }, {
                UnblockUserResult.Success
            })
}

sealed class UnblockUserResult {
    data object Success : UnblockUserResult()
    class Failure(val coreFailure: CoreFailure) : UnblockUserResult()
}

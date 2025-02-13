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

/**
 * Use Case that allows a user to block user
 */
fun interface BlockUserUseCase {
    /**
     * Use case [BlockUserUseCase] operation
     *
     * @param userId the target user whom to block
     * @return a [BlockUserResult] indicating the operation result
     */
    suspend operator fun invoke(userId: UserId): BlockUserResult
}

internal class BlockUserUseCaseImpl(
    private val connectionRepository: ConnectionRepository
) : BlockUserUseCase {

    override suspend fun invoke(userId: UserId): BlockUserResult {
        return connectionRepository.updateConnectionStatus(userId, ConnectionState.BLOCKED)
            .fold({
                kaliumLogger.e("An error occurred when blocking a user $userId")
                BlockUserResult.Failure(it)
            }, {
                BlockUserResult.Success
            })
    }
}

sealed class BlockUserResult {
    data object Success : BlockUserResult()
    data class Failure(val coreFailure: CoreFailure) : BlockUserResult()
}

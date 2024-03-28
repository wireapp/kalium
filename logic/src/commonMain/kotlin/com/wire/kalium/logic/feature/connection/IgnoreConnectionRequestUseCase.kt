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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger

/**
 * Use Case that allows a user to ignore a connection request from given user
 */
fun interface IgnoreConnectionRequestUseCase {
    /**
     * Use case [IgnoreConnectionRequestUseCase] operation
     *
     * @param userId the target user with whom to ignore the connection request
     * @return a [IgnoreConnectionRequestUseCaseResult] indicating the operation result
     */
    suspend operator fun invoke(userId: UserId): IgnoreConnectionRequestUseCaseResult
}

internal class IgnoreConnectionRequestUseCaseImpl(
    private val connectionRepository: ConnectionRepository
) : IgnoreConnectionRequestUseCase {

    override suspend fun invoke(userId: UserId): IgnoreConnectionRequestUseCaseResult {
        return connectionRepository.ignoreConnectionRequest(userId)
            .fold({
                kaliumLogger.e("An error occurred when ignoring the connection request to $userId")
                IgnoreConnectionRequestUseCaseResult.Failure(it)
            }, {
                IgnoreConnectionRequestUseCaseResult.Success
            })
    }
}

sealed class IgnoreConnectionRequestUseCaseResult {
    data object Success : IgnoreConnectionRequestUseCaseResult()
    data class Failure(val coreFailure: CoreFailure) : IgnoreConnectionRequestUseCaseResult()
}

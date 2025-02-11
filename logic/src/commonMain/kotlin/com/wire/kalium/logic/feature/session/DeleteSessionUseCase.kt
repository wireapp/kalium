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

package com.wire.kalium.logic.feature.session

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.onSuccess
import kotlinx.coroutines.cancel

/**
 * This class is responsible for deleting a user session and freeing up all the resources.
 */
class DeleteSessionUseCase internal constructor(
    private val sessionRepository: SessionRepository,
    private val userSessionScopeProvider: UserSessionScopeProvider
) {
    suspend operator fun invoke(userId: UserId) = sessionRepository.deleteSession(userId)
        .onSuccess {
            userSessionScopeProvider.get(userId)?.cancel()
            userSessionScopeProvider.delete(userId)
        }.fold({
            Result.Failure(it)
        }, {
            Result.Success
        })

    sealed class Result {
        data object Success : Result()
        data class Failure(val cause: StorageFailure) : Result()
    }
}

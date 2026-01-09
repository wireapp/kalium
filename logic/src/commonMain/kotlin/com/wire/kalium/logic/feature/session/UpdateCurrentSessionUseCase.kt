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
import com.wire.kalium.common.functional.fold

/**
 * This use case will change the current session for the given user id.
 * ie: Use this use case to switch between users.
 *
 * @see [UpdateCurrentSessionUseCase.Result]
 */
// todo(interface). extract interface for use case
public class UpdateCurrentSessionUseCase internal constructor(private val sessionRepository: SessionRepository) {
    public suspend operator fun invoke(userId: UserId?): Result =
        sessionRepository.updateCurrentSession(userId).fold({ Result.Failure(it) }, { Result.Success })

    public sealed class Result {
        public data object Success : Result()
        public data class Failure(public val cause: StorageFailure) : Result()
    }
}

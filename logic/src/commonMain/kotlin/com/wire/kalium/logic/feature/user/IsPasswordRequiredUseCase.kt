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

package com.wire.kalium.logic.feature.user

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map

/**
 * Checks if the current requires password to authenticate operations.
 * In case the user doesn't have a password, means is an SSO user.
 */
// TODO: rename to HasSAMLCredentialsUseCase
class IsPasswordRequiredUseCase internal constructor(
    private val selfUserId: UserId,
    private val sessionRepository: SessionRepository,
) {
    /**
     * @return [Result] with [Boolean] true if the user requires password, false otherwise.
     */
    suspend operator fun invoke(): Result = eitherInvoke().fold({
        Result.Failure(it)
    }, {
        Result.Success(it)
    })

    internal suspend fun eitherInvoke(): Either<StorageFailure, Boolean> = sessionRepository.ssoId(selfUserId).map {
        it?.subject.isNullOrBlank()
    }

    sealed class Result {
        data class Success(val value: Boolean) : Result()
        data class Failure(val cause: StorageFailure) : Result()
    }
}

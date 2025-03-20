/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.auth

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.user.UserRepository

sealed class PersistSelfUserEmailResult {
    data object Success : PersistSelfUserEmailResult()
    data class Failure(val coreFailure: CoreFailure) : PersistSelfUserEmailResult()
}

interface PersistSelfUserEmailUseCase {
    /**
     * Persists the email of the self user before full data is fetched so that it can be used to complete the client registration with 2fa.
     * @param email The email address of the self user
     */
    suspend operator fun invoke(email: String): PersistSelfUserEmailResult
}

internal class PersistSelfUserEmailUseCaseImpl(
    private val userRepository: UserRepository,
) : PersistSelfUserEmailUseCase {
    override suspend fun invoke(email: String): PersistSelfUserEmailResult =
        userRepository.insertSelfIncompleteUserWithOnlyEmail(email).let {
            when (it) {
                is Either.Left -> PersistSelfUserEmailResult.Failure(it.value)
                is Either.Right -> PersistSelfUserEmailResult.Success
            }
        }
}

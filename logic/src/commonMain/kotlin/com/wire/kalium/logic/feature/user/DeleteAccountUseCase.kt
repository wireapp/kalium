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

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.user.AccountRepository
import com.wire.kalium.common.functional.fold

/**
 * Use case for deleting the user account.
 */
class DeleteAccountUseCase internal constructor(
    private val accountRepository: AccountRepository
) {
    suspend operator fun invoke(password: String?): Result =
        accountRepository.deleteAccount(password)
            .fold(Result::Failure, { Result.Success })

    sealed class Result {
        data object Success : Result()
        data class Failure(val networkFailure: NetworkFailure) : Result()
    }
}

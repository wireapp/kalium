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
package com.wire.kalium.logic.util.arrangement.repository

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.user.AccountRepository
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.common.functional.Either
import io.mockative.coEvery
import io.mockative.fake.valueOf
import io.mockative.matchers.AnyMatcher
import io.mockative.matchers.Matcher
import io.mockative.matches
import io.mockative.mock

internal interface AccountRepositoryArrangement {

    val accountRepository: AccountRepository
    suspend fun withUpdateSelfUserAvailabilityStatus(
        result: Either<StorageFailure, Unit>,
        newStatus: Matcher<UserAvailabilityStatus> = AnyMatcher(valueOf())
    )
}

internal class AccountRepositoryArrangementImpl : AccountRepositoryArrangement {

    override val accountRepository: AccountRepository = mock(AccountRepository::class)

    override suspend fun withUpdateSelfUserAvailabilityStatus(
        result: Either<StorageFailure, Unit>,
        newStatus: Matcher<UserAvailabilityStatus>
    ) {
        coEvery {
            accountRepository.updateSelfUserAvailabilityStatus(matches { newStatus.matches(it) })
        }.returns(result)
    }

}

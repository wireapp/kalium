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
package com.wire.kalium.logic.util.arrangement.repository

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.matchers.Matcher
import io.mockative.mock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal interface UserRepositoryArrangement {
    val userRepository: UserRepository
    fun withDefederateUser(result: Either<CoreFailure, Unit>, userId: Matcher<UserId> = any())
    fun withObserveUser(result: Flow<User?> = flowOf(TestUser.OTHER), userId: Matcher<UserId> = any())
}

internal open class UserRepositoryArrangementImpl : UserRepositoryArrangement {
    @Mock
    override val userRepository: UserRepository = mock(UserRepository::class)

    override fun withDefederateUser(
        result: Either<CoreFailure, Unit>,
        userId: Matcher<UserId>
    ) {
        given(userRepository)
            .suspendFunction(userRepository::defederateUser)
            .whenInvokedWith(userId)
            .thenReturn(result)
    }

    override fun withObserveUser(result: Flow<User?>, userId: Matcher<UserId>) {
        given(userRepository)
            .suspendFunction(userRepository::observeUser)
            .whenInvokedWith(userId)
            .thenReturn(result)
    }
}

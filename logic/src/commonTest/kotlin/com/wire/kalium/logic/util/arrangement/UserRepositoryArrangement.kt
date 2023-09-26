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
package com.wire.kalium.logic.util.arrangement

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.flow.Flow

internal interface UserRepositoryArrangement {
    val userRepository: UserRepository

    fun withUpdateUserSuccess()

    fun withRemoveUserSuccess()

    fun withSelfUserReturning(selfUser: SelfUser?)

    fun withUserByIdReturning(result: Either<CoreFailure, OtherUser>)

    fun withUpdateOneOnOneConversationReturning(result: Either<CoreFailure, Unit>)

    fun withGetKnownUserReturning(result: Flow<OtherUser?>)

    fun withGetUsersWithOneOnOneConversationReturning(result: List<OtherUser>)

    fun withFetchAllOtherUsersReturning(result: Either<CoreFailure, Unit>)
}

internal class UserRepositoryArrangementImpl: UserRepositoryArrangement {

    @Mock
    override val userRepository: UserRepository = mock(UserRepository::class)

    override fun withUpdateUserSuccess() {
        given(userRepository).suspendFunction(userRepository::updateUserFromEvent).whenInvokedWith(any())
            .thenReturn(Either.Right(Unit))
    }

    override fun withRemoveUserSuccess() {
        given(userRepository).suspendFunction(userRepository::removeUser)
            .whenInvokedWith(any()).thenReturn(Either.Right(Unit))
    }

    override fun withSelfUserReturning(selfUser: SelfUser?) {
        given(userRepository)
            .suspendFunction(userRepository::getSelfUser)
            .whenInvoked()
            .thenReturn(selfUser)
    }

    override fun withUserByIdReturning(result: Either<CoreFailure, OtherUser>) {
        given(userRepository)
            .suspendFunction(userRepository::userById)
            .whenInvokedWith(any())
            .thenReturn(result)
    }

    override fun withUpdateOneOnOneConversationReturning(result: Either<CoreFailure, Unit>) {
        given(userRepository)
            .suspendFunction(userRepository::updateActiveOneOnOneConversation)
            .whenInvokedWith(any())
            .thenReturn(result)
    }

    override fun withGetKnownUserReturning(result: Flow<OtherUser?>) {
        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .thenReturn(result)
    }

    override fun withGetUsersWithOneOnOneConversationReturning(result: List<OtherUser>) {
        given(userRepository)
            .suspendFunction(userRepository::getUsersWithOneOnOneConversation)
            .whenInvoked()
            .thenReturn(result)
    }

    override fun withFetchAllOtherUsersReturning(result: Either<CoreFailure, Unit>) {
        given(userRepository)
            .suspendFunction(userRepository::fetchAllOtherUsers)
            .whenInvoked()
            .thenReturn(result)
    }

}

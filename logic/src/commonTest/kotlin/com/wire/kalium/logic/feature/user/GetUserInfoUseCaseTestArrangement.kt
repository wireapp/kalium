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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.flow.flowOf

internal class GetUserInfoUseCaseTestArrangement {

    @Mock
    val userRepository: UserRepository = mock(UserRepository::class)

    @Mock
    val teamRepository: TeamRepository = mock(TeamRepository::class)

    fun withSuccessfulUserRetrieve(
        localUserPresent: Boolean = true,
        hasTeam: Boolean = true,
        userType: UserType = UserType.EXTERNAL
    ): GetUserInfoUseCaseTestArrangement {
        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .thenReturn(
                flowOf(
                    if (!localUserPresent) null
                    else if (hasTeam) TestUser.OTHER.copy(userType = userType)
                    else TestUser.OTHER.copy(teamId = null, userType = userType)
                )
            )

        if (!localUserPresent) {
            given(userRepository)
                .suspendFunction(userRepository::userById)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(TestUser.OTHER))
        }

        return this
    }

    fun withFailingUserRetrieve(): GetUserInfoUseCaseTestArrangement {
        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .thenReturn(flowOf(null))

        given(userRepository)
            .suspendFunction(userRepository::userById)
            .whenInvokedWith(any())
            .thenReturn(
                Either.Left(CoreFailure.Unknown(RuntimeException("some error")))
            )

        return this
    }

    fun withSuccessfulTeamRetrieve(
        localTeamPresent: Boolean = true,
    ): GetUserInfoUseCaseTestArrangement {
        given(teamRepository)
            .suspendFunction(teamRepository::getTeam)
            .whenInvokedWith(any())
            .thenReturn(
                flowOf(
                    if (!localTeamPresent) null
                    else TestTeam.TEAM
                )
            )

        if (!localTeamPresent) {
            given(teamRepository)
                .suspendFunction(teamRepository::fetchTeamById)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(TestTeam.TEAM))
        }

        return this
    }

    fun withFailingTeamRetrieve(): GetUserInfoUseCaseTestArrangement {
        given(teamRepository)
            .suspendFunction(teamRepository::getTeam)
            .whenInvokedWith(any())
            .thenReturn(
                flowOf(null)
            )

        given(teamRepository)
            .suspendFunction(teamRepository::fetchTeamById)
            .whenInvokedWith(any())
            .thenReturn(
                Either.Left(CoreFailure.Unknown(RuntimeException("some error")))
            )

        return this
    }

    fun arrange(): Pair<GetUserInfoUseCaseTestArrangement, GetUserInfoUseCase> {
        return this to GetUserInfoUseCaseImpl(userRepository, teamRepository)
    }

}

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
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.flow.flowOf

internal class GetUserInfoUseCaseTestArrangement {

    @Mock
    val userRepository: UserRepository = mock(UserRepository::class)

    @Mock
    val teamRepository: TeamRepository = mock(TeamRepository::class)

    suspend fun withSuccessfulUserRetrieve(
        localUserPresent: Boolean = true,
        hasTeam: Boolean = true,
        userType: UserType = UserType.EXTERNAL
    ): GetUserInfoUseCaseTestArrangement {
        coEvery {
            userRepository.getKnownUser(any())
        }.returns(
                flowOf(
                    if (!localUserPresent) null
                    else if (hasTeam) TestUser.OTHER.copy(userType = userType)
                    else TestUser.OTHER.copy(teamId = null, userType = userType)
                )
            )

        if (!localUserPresent) {
            coEvery {
                userRepository.userById(any())
            }.returns(Either.Right(TestUser.OTHER))
        }

        return this
    }

    suspend fun withFailingUserRetrieve(): GetUserInfoUseCaseTestArrangement {
        coEvery {
            userRepository.getKnownUser(any())
        }.returns(flowOf(null))

        coEvery {
            userRepository.userById(any())
        }.returns(Either.Left(CoreFailure.Unknown(RuntimeException("some error"))))

        return this
    }

    suspend fun withSuccessfulTeamRetrieve(
        localTeamPresent: Boolean = true,
    ): GetUserInfoUseCaseTestArrangement {
        coEvery {
            teamRepository.getTeam(any())
        }.returns(
                flowOf(
                    if (!localTeamPresent) null
                    else TestTeam.TEAM
                )
            )

        if (!localTeamPresent) {
            coEvery {
                teamRepository.fetchTeamById(any())
            }.returns(Either.Right(TestTeam.TEAM))
        }

        return this
    }

    suspend fun withFailingTeamRetrieve(): GetUserInfoUseCaseTestArrangement {
        coEvery {
            teamRepository.getTeam(any())
        }.returns(flowOf(null))

        coEvery {
            teamRepository.fetchTeamById(any())
        }.returns(Either.Left(CoreFailure.Unknown(RuntimeException("some error"))))

        return this
    }

    fun arrange(): Pair<GetUserInfoUseCaseTestArrangement, GetUserInfoUseCase> {
        return this to GetUserInfoUseCaseImpl(userRepository, teamRepository)
    }

}

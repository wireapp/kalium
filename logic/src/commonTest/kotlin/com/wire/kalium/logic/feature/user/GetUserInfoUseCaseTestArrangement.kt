package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.flow.flowOf

class GetUserInfoUseCaseTestArrangement {

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
                    else if (hasTeam) TestUser.OTHER.copy(userType = userType) else TestUser.OTHER.copy(team = null, userType = userType)
                )
            )

        if (!localUserPresent) {
            given(userRepository)
                .suspendFunction(userRepository::getUserInfo)
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
            .suspendFunction(userRepository::getUserInfo)
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
                    else team
                )
            )

        if (!localTeamPresent) {
            given(teamRepository)
                .suspendFunction(teamRepository::fetchTeamById)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(team))
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

    private companion object {
        val team = Team("teamId", "teamName")
    }
}

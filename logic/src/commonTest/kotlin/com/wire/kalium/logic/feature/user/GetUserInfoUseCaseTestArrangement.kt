package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.self.SelfUserRepository
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.flow.flowOf

class GetUserInfoUseCaseTestArrangement {

    @Mock
    val selfUserRepository: SelfUserRepository = mock(SelfUserRepository::class)

    @Mock
    val teamRepository: TeamRepository = mock(TeamRepository::class)

    fun withSuccessfulUserRetrieve(
        localUserPresent: Boolean = true,
        hasTeam: Boolean = true
    ): GetUserInfoUseCaseTestArrangement {
        given(selfUserRepository)
            .suspendFunction(selfUserRepository::getKnownUser)
            .whenInvokedWith(any())
            .thenReturn(
                flowOf(
                    if (!localUserPresent) null
                    else if (hasTeam) TestUser.OTHER else TestUser.OTHER.copy(team = null)
                )
            )

        if (!localUserPresent) {
            given(selfUserRepository)
                .suspendFunction(selfUserRepository::fetchUserInfo)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(TestUser.OTHER))
        }

        return this
    }

    fun withFailingUserRetrieve(): GetUserInfoUseCaseTestArrangement {
        given(selfUserRepository)
            .suspendFunction(selfUserRepository::getKnownUser)
            .whenInvokedWith(any())
            .thenReturn(flowOf(null))

        given(selfUserRepository)
            .suspendFunction(selfUserRepository::fetchUserInfo)
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
        return this to GetUserInfoUseCaseImpl(selfUserRepository, teamRepository)
    }

    private companion object {
        val team = Team("teamId", "teamName")
    }
}

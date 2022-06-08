package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.flow.flowOf
import kotlin.test.BeforeTest

class GetUserInfoUseCaseTestArrangement {

    @Mock
    private val userRepository: UserRepository = mock(UserRepository::class)

    @Mock
    private val teamRepository: TeamRepository = mock(TeamRepository::class)

    lateinit var getUserInfoUseCase: GetUserInfoUseCase

    @BeforeTest
    fun setUp() {
        getUserInfoUseCase = GetUserInfoUseCaseImpl(userRepository, teamRepository)
    }

    fun withSuccessFullUserRetrive(
        localUserPresent: Boolean = true,
        hasTeam: Boolean = true
    ): GetUserInfoUseCaseTestArrangement {
        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .thenReturn(
                flowOf(
                    if (!localUserPresent) null
                    else if (hasTeam) TestUser.OTHER else TestUser.OTHER.copy(team = null)
                )
            )

        if (localUserPresent) {
            given(userRepository)
                .suspendFunction(userRepository::fetchUserInfo)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(TestUser.OTHER))
        }

        return this
    }

    fun withFailingUserRetrive() {
        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(eq(userId))
            .thenReturn(flowOf(TestUser.OTHER))

        given(userRepository)
            .suspendFunction(userRepository::fetchUserInfo)
            .whenInvokedWith(any())
            .thenReturn(
                Either.Left(CoreFailure.Unknown(RuntimeException("some error")))
            )
    }

    private companion object {
        val userId = UserId("some_user", "some_domain")

        val team = Team("teamId", "teamName")
    }
}

package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestUser.OTHER
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GetUserInfoUseCaseTest {

    @Mock
    private val userRepository: UserRepository = mock(UserRepository::class)

    @Mock
    private val teamRepository: TeamRepository = mock(TeamRepository::class)

    lateinit var getUserInfoUseCase: GetUserInfoUseCase

    @BeforeTest
    fun setUp() {
        getUserInfoUseCase = GetUserInfoUseCaseImpl(userRepository, teamRepository)
    }

    @Test
    fun givenAUserId_whenInvokingGetUserInfoDetails_thenShouldReturnsASuccessResult() = runTest {
        // given
        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(eq(userId))
            .thenReturn(flowOf(null))

        given(teamRepository)
            .suspendFunction(teamRepository::getTeam)
            .whenInvokedWith(any())
            .thenReturn(flowOf(team))

        given(userRepository)
            .suspendFunction(userRepository::fetchUserInfo)
            .whenInvokedWith(eq(userId))
            .thenReturn(Either.Right(OTHER))
        // when
        val result = getUserInfoUseCase(userId)

        // then
        assertEquals(OTHER, (result as GetUserInfoResult.Success).otherUser)
        verify(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .with(eq(userId))
            .wasInvoked(once)

        verify(userRepository)
            .suspendFunction(userRepository::fetchUserInfo)
            .with(eq(userId))
            .wasInvoked(once)
    }


    @Test
    fun givenAUserId_whenInvokingGetUserInfoDetailsAndExistsLocally_thenShouldReturnsImmediatelySuccessResult() = runTest {
        // given
        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(eq(userId))
            .thenReturn(flowOf(OTHER))

        given(teamRepository)
            .suspendFunction(teamRepository::getTeam)
            .whenInvokedWith(any())
            .thenReturn(flowOf(team))
        // when
        val result = getUserInfoUseCase(userId)

        // then
        assertEquals(OTHER, (result as GetUserInfoResult.Success).otherUser)
        verify(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .with(eq(userId))
            .wasInvoked(once)

        verify(userRepository)
            .suspendFunction(userRepository::fetchUserInfo)
            .with(eq(userId))
            .wasNotInvoked()
    }


    @Test
    fun givenAUserId_whenInvokingGetUserInfoDetailsWithErrors_thenShouldReturnsAFailure() = runTest {
        // given
        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(eq(userId))
            .thenReturn(flowOf(null))

        given(userRepository)
            .suspendFunction(userRepository::fetchUserInfo)
            .whenInvokedWith(eq(userId))
            .thenReturn(Either.Left(CoreFailure.Unknown(RuntimeException("some error"))))

        // when
        val result = getUserInfoUseCase(userId)

        // then
        assertEquals(GetUserInfoResult.Failure, result)

        verify(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .with(eq(userId))
            .wasInvoked(once)

        verify(userRepository)
            .suspendFunction(userRepository::fetchUserInfo)
            .with(eq(userId))
            .wasInvoked(once)
    }


    @Test
    fun givenAUserWithNoTeam_WhenGettingDetails_thenShouldReturnSuccessResultAndDoNotRetrieveTeam() = runTest {
        // given
        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(eq(userId))
            .thenReturn(flowOf(OTHER.copy(team = null)))

        given(teamRepository)
            .suspendFunction(teamRepository::getTeam)
            .whenInvokedWith(any())
            .thenReturn(flowOf(team))
        // when
        val result = getUserInfoUseCase(userId)

        // then
        assertEquals(OTHER.copy(team = null), (result as GetUserInfoResult.Success).otherUser)

        verify(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .with(eq(userId))
            .wasInvoked(once)

        verify(teamRepository)
            .suspendFunction(teamRepository::getTeam)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenAUserWithTeamNotExistingLocally_WhenGettingDetails_thenShouldReturnSuccessResultAndGetRemoteUserTeam() = runTest {
        // given
        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(eq(userId))
            .thenReturn(flowOf(OTHER))

        given(teamRepository)
            .suspendFunction(teamRepository::getTeam)
            .whenInvokedWith(any())
            .thenReturn(flowOf(null))

        given(teamRepository)
            .suspendFunction(teamRepository::fetchTeamById)
            .whenInvokedWith(any())
            .thenReturn(Either.Right(team))
        // when
        val result = getUserInfoUseCase(userId)

        // then
        assertEquals(OTHER, (result as GetUserInfoResult.Success).otherUser)

        verify(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .with(eq(userId))
            .wasInvoked(once)

        verify(teamRepository)
            .suspendFunction(teamRepository::getTeam)
            .with(any())
            .wasInvoked(once)

        verify(teamRepository)
            .suspendFunction(teamRepository::fetchTeamById)
            .with(any())
            .wasInvoked(once)
    }

    @Test
    fun givenAUserWithTeamNotExistingLocallyAndFetchingUserInfoFails_WhenGettingDetails_ThenPropagateTheFailureAndDoNotGetTheTeamDetails() = runTest {
        // given
        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(eq(userId))
            .thenReturn(flowOf(null))

        given(userRepository)
            .suspendFunction(userRepository::fetchUserInfo)
            .whenInvokedWith(any())
            .thenReturn(Either.Left(CoreFailure.Unknown(RuntimeException("some error"))))

        // when
        val result = getUserInfoUseCase(userId)

        // then
        assertIs<GetUserInfoResult.Failure>(result)

        verify(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .with(eq(userId))
            .wasInvoked(once)

        verify(userRepository)
            .suspendFunction(userRepository::fetchUserInfo)
            .with(any())
            .wasInvoked(once)

        verify(teamRepository)
            .suspendFunction(teamRepository::getTeam)
            .with(any())
            .wasNotInvoked()

        verify(teamRepository)
            .suspendFunction(teamRepository::fetchTeamById)
            .with(any())
            .wasInvoked()
    }

    @Test
    fun givenAUserWithTeamNotExistingLocallyAndFetchingTeamFails_WhenGettingDetails_ThenPropagateTheFailure() = runTest {
        // given
        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(eq(userId))
            .thenReturn(flowOf(OTHER))

        given(teamRepository)
            .suspendFunction(teamRepository::getTeam)
            .whenInvokedWith(any())
            .thenReturn(flowOf(null))

        given(teamRepository)
            .suspendFunction(teamRepository::fetchTeamById)
            .whenInvokedWith(any())
            .thenReturn(Either.Left(CoreFailure.Unknown(RuntimeException("some error"))))
        // when
        val result = getUserInfoUseCase(userId)

        // then
        assertIs<GetUserInfoResult.Failure>(result)

        verify(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .with(eq(userId))
            .wasInvoked(once)

        verify(teamRepository)
            .suspendFunction(teamRepository::getTeam)
            .with(any())
            .wasInvoked(once)

        verify(teamRepository)
            .suspendFunction(teamRepository::fetchTeamById)
            .with(any())
            .wasInvoked(once)
    }


    private companion object {
        val userId = UserId("some_user", "some_domain")

        val team = Team("teamId", "teamName")
    }

}


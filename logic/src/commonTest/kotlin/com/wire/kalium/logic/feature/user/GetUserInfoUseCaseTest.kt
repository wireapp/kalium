package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestUser.OTHER
import io.mockative.any
import io.mockative.eq
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GetUserInfoUseCaseTest {

    private val arrangement = GetUserInfoUseCaseTestArrangement()

    @Test
    fun givenAUserId_whenInvokingGetUserInfoDetails_thenShouldReturnsASuccessResult() = runTest {
        // given
        val (arrangement, useCase) = arrangement
            .withSuccessFullUserRetrive(localUserPresent = false)
            .withSuccessTeamRetrive(localTeamPresent = true)
            .arrange()

        // when
        val result = useCase(userId)

        // then
        assertEquals(OTHER, (result as GetUserInfoResult.Success).otherUser)

        with(arrangement) {
            verify(userRepository)
                .suspendFunction(userRepository::getKnownUser)
                .with(eq(userId))
                .wasInvoked(once)

            verify(userRepository)
                .suspendFunction(userRepository::fetchUserInfo)
                .with(eq(userId))
                .wasInvoked(once)
        }
    }

    @Test
    fun givenAUserId_whenInvokingGetUserInfoDetailsAndExistsLocally_thenShouldReturnsImmediatelySuccessResult() = runTest {
        // given
        val (arrangement, useCase) = arrangement
            .withSuccessFullUserRetrive(localUserPresent = true)
            .withSuccessTeamRetrive(localTeamPresent = true)
            .arrange()

        // when
        val result = useCase(userId)

        // then
        assertEquals(OTHER, (result as GetUserInfoResult.Success).otherUser)

        with(arrangement) {
            verify(userRepository)
                .suspendFunction(userRepository::getKnownUser)
                .with(eq(userId))
                .wasInvoked(once)

            verify(userRepository)
                .suspendFunction(userRepository::fetchUserInfo)
                .with(eq(userId))
                .wasNotInvoked()
        }
    }

    @Test
    fun givenAUserId_whenInvokingGetUserInfoDetailsWithErrors_thenShouldReturnsAFailure() = runTest {
        // given
        val (arrangement, useCase) = arrangement
            .withFailingUserRetrive()
            .withSuccessTeamRetrive()
            .arrange()

        // when
        val result = useCase(userId)

        // then
        assertEquals(GetUserInfoResult.Failure, result)

        with(arrangement) {
            verify(userRepository)
                .suspendFunction(userRepository::getKnownUser)
                .with(eq(userId))
                .wasInvoked(once)

            verify(userRepository)
                .suspendFunction(userRepository::fetchUserInfo)
                .with(eq(userId))
                .wasInvoked(once)
        }
    }

    @Test
    fun givenAUserWithNoTeam_WhenGettingDetails_thenShouldReturnSuccessResultAndDoNotRetrieveTeam() = runTest {
        // given
        val (arrangement, useCase) = arrangement
            .withSuccessFullUserRetrive(hasTeam = false)
            .arrange()

        // when
        val result = useCase(userId)

        // then
        assertEquals(OTHER.copy(team = null), (result as GetUserInfoResult.Success).otherUser)

        with(arrangement) {
            verify(userRepository)
                .suspendFunction(userRepository::getKnownUser)
                .with(eq(userId))
                .wasInvoked(once)

            verify(teamRepository)
                .suspendFunction(teamRepository::getTeam)
                .with(any())
                .wasNotInvoked()
        }
    }

    @Test
    fun givenAUserWithTeamNotExistingLocally_WhenGettingDetails_thenShouldReturnSuccessResultAndGetRemoteUserTeam() = runTest {
        // given
        val (arrangement, useCase) = arrangement
            .withSuccessFullUserRetrive()
            .withSuccessTeamRetrive(localTeamPresent = false)
            .arrange()

        // when
        val result = useCase(userId)

        // then
        assertEquals(OTHER, (result as GetUserInfoResult.Success).otherUser)

        with(arrangement) {
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
    }

    @Test
    fun givenAUserWithTeamNotExistingLocallyAndFetchingUserInfoFails_WhenGettingDetails_ThenPropagateTheFailureAndDoNotGetTheTeamDetails() =
        runTest {
            // given
            val (arrangement, useCase) = arrangement
                .withFailingUserRetrive()
                .withFailingTeamRetrieve()
                .arrange()
            // when
            val result = useCase(userId)

            // then
            assertIs<GetUserInfoResult.Failure>(result)

            with(arrangement) {
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
        }

    @Test
    fun givenAUserWithTeamNotExistingLocallyAndFetchingTeamFails_WhenGettingDetails_ThenPropagateTheFailure() = runTest {
        // given

        val (arrangement, useCase) = arrangement
            .withSuccessFullUserRetrive(localUserPresent = true)
            .withFailingTeamRetrieve()
            .arrange()

        // when
        val result = useCase(userId)

        // then
        assertIs<GetUserInfoResult.Failure>(result)

        with(arrangement) {
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
    }

    private companion object {
        val userId = UserId("some_user", "some_domain")
    }

}




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

import app.cash.turbine.test
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.framework.TestUser
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ObserveUserInfoUseCaseTest {

    private val arrangement = ObserveUserInfoUseCaseTestArrangement()

    @Test
    fun givenAUserIdWhichIsInDB_whenInvokingObserveUserInfo_thenShouldReturnsSuccessResult() = runTest {
        // given
        val (arrangement, useCase) = arrangement
            .withSuccessfulUserRetrieveFromDB()
            .withSuccessfulTeamRetrieve(localTeamPresent = true)
            .arrange()

        useCase(userId).test {
            val result = awaitItem()
            assertEquals(TestUser.OTHER, (result as GetUserInfoResult.Success).otherUser)

            with(arrangement) {
                verify(userRepository)
                    .suspendFunction(userRepository::fetchUsersByIds)
                    .with(any())
                    .wasNotInvoked()
            }

            awaitComplete()
        }
    }

    @Test
    fun givenAUserIdWhichIsNotInDB_whenInvokingObserveUserInfo_thenFetchKnownUsersIsCalled() = runTest {
        // given
        val (arrangement, useCase) = arrangement
            .withFailingUserRetrieveFromDB()
            .withSuccessfulTeamRetrieve(localTeamPresent = true)
            .withSuccessfulUserFetching()
            .arrange()

        useCase(userId).test {
            awaitComplete()

            with(arrangement) {
                verify(userRepository)
                    .suspendFunction(userRepository::fetchUsersByIds)
                    .with(any())
                    .wasInvoked(once)
            }

        }
    }

    @Test
    fun givenAUserIdWhichIsNotInDBAndFetchUsersByIdsReturnsError_whenInvokingObserveUserInfo_thenErrorIsPropagated() = runTest {
        // given
        val (arrangement, useCase) = arrangement
            .withFailingUserRetrieveFromDB()
            .withSuccessfulTeamRetrieve(localTeamPresent = true)
            .withFailingUserFetching()
            .arrange()

        useCase(userId).test {
            val result = awaitItem()

            assertIs<GetUserInfoResult.Failure>(result)

            with(arrangement) {
                verify(userRepository)
                    .suspendFunction(userRepository::fetchUsersByIds)
                    .with(any())
                    .wasInvoked(once)
            }

            awaitComplete()
        }
    }

    @Test
    fun givenAUserWithNoTeam_WhenGettingDetails_thenShouldReturnSuccessResultAndDoNotRetrieveTeam() = runTest {
        // given
        val (arrangement, useCase) = arrangement
            .withSuccessfulUserRetrieveFromDB(hasTeam = false)
            .arrange()

        // when
        useCase(userId).test {
            val result = awaitItem()

            // then
            assertEquals(TestUser.OTHER.copy(teamId = null), (result as GetUserInfoResult.Success).otherUser)

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
            awaitComplete()
        }
    }

    @Test
    fun givenAInternalUserWithTeamNotExistingLocally_WhenGettingDetails_thenShouldReturnSuccessResultAndGetRemoteUserTeam() = runTest {
        // given
        val (arrangement, useCase) = arrangement
            .withSuccessfulUserRetrieveFromDB(userType = UserType.INTERNAL)
            .withSuccessfulTeamRetrieve(localTeamPresent = false)
            .arrange()

        // when
        useCase(userId).test {
            val result = awaitItem()

            // then
            assertEquals(TestUser.OTHER.copy(userType = UserType.INTERNAL), (result as GetUserInfoResult.Success).otherUser)

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
            awaitComplete()
        }
    }

    @Test
    fun givenAUserWithTeamNotExistingLocally_WhenGettingDetails_DoNotGetTheTeamDetails() =
        runTest {
            // given
            val (arrangement, useCase) = arrangement
                .withFailingUserRetrieveFromDB()
                .withSuccessfulUserFetching()
                .withFailingTeamRetrieve()
                .arrange()
            // when
            useCase(userId).test {
                with(arrangement) {
                    verify(userRepository)
                        .suspendFunction(userRepository::getKnownUser)
                        .with(eq(userId))
                        .wasInvoked(once)

                    verify(teamRepository)
                        .suspendFunction(teamRepository::getTeam)
                        .with(any())
                        .wasNotInvoked()

                    verify(teamRepository)
                        .suspendFunction(teamRepository::fetchTeamById)
                        .with(any())
                        .wasNotInvoked()
                }
                awaitComplete()
            }
        }

    @Test
    fun givenAInternalUserWithTeamNotExistingLocallyAndFetchingTeamFails_WhenGettingDetails_ThenPropagateTheFailure() = runTest {
        // given

        val (arrangement, useCase) = arrangement
            .withSuccessfulUserRetrieveFromDB(userType = UserType.INTERNAL)
            .withFailingTeamRetrieve()
            .arrange()

        // when
        useCase(userId).test {
            val result = awaitItem()

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
            awaitComplete()
        }
    }

    private class ObserveUserInfoUseCaseTestArrangement {

        @Mock
        val userRepository: UserRepository = mock(UserRepository::class)

        @Mock
        val teamRepository: TeamRepository = mock(TeamRepository::class)

        fun withSuccessfulUserRetrieveFromDB(
            hasTeam: Boolean = true,
            userType: UserType = UserType.EXTERNAL
        ): ObserveUserInfoUseCaseTestArrangement {
            given(userRepository)
                .suspendFunction(userRepository::getKnownUser)
                .whenInvokedWith(any())
                .thenReturn(
                    flowOf(
                        if (hasTeam) TestUser.OTHER.copy(userType = userType) else TestUser.OTHER.copy(
                            teamId = null,
                            userType = userType
                        )
                    )
                )

            return this
        }

        fun withFailingUserRetrieveFromDB(): ObserveUserInfoUseCaseTestArrangement {
            given(userRepository)
                .suspendFunction(userRepository::getKnownUser)
                .whenInvokedWith(any())
                .thenReturn(flowOf(null))

            return this
        }

        fun withFailingUserFetching(): ObserveUserInfoUseCaseTestArrangement {
            given(userRepository)
                .suspendFunction(userRepository::fetchUsersByIds)
                .whenInvokedWith(any())
                .thenReturn(Either.Left(CoreFailure.Unknown(RuntimeException("fetchUsersByIds error"))))

            return this
        }

        fun withSuccessfulUserFetching(): ObserveUserInfoUseCaseTestArrangement {
            given(userRepository)
                .suspendFunction(userRepository::fetchUsersByIds)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))

            return this
        }

        fun withSuccessfulTeamRetrieve(
            localTeamPresent: Boolean = true,
        ): ObserveUserInfoUseCaseTestArrangement {
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

        fun withFailingTeamRetrieve(): ObserveUserInfoUseCaseTestArrangement {
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

        fun arrange(): Pair<ObserveUserInfoUseCaseTestArrangement, ObserveUserInfoUseCase> {
            return this to ObserveUserInfoUseCaseImpl(userRepository, teamRepository)
        }

    }

    private companion object {
        val userId = UserId("some_user", "some_domain")
    }
}

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

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.framework.TestUser.OTHER
import io.mockative.any
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.once
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
            .withSuccessfulUserRetrieve(localUserPresent = false)
            .withSuccessfulTeamRetrieve(localTeamPresent = true)
            .arrange()

        // when
        val result = useCase(userId)

        // then
        assertEquals(OTHER, (result as GetUserInfoResult.Success).otherUser)

        with(arrangement) {
            coVerify {
                userRepository.getKnownUser(eq(userId))
            }.wasInvoked(once)

            coVerify {
                userRepository.userById(eq(userId))
            }.wasInvoked(once)
        }
    }

    @Test
    fun givenAUserId_whenInvokingGetUserInfoDetailsAndExistsLocally_thenShouldReturnsImmediatelySuccessResult() = runTest {
        // given
        val (arrangement, useCase) = arrangement
            .withSuccessfulUserRetrieve(localUserPresent = true)
            .withSuccessfulTeamRetrieve(localTeamPresent = true)
            .arrange()

        // when
        val result = useCase(userId)

        // then
        assertEquals(OTHER, (result as GetUserInfoResult.Success).otherUser)

        with(arrangement) {
            coVerify {
                userRepository.getKnownUser(eq(userId))
            }.wasInvoked(once)

            coVerify {
                userRepository.userById(eq(userId))
            }.wasNotInvoked()
        }
    }

    @Test
    fun givenAUserId_whenInvokingGetUserInfoDetailsWithErrors_thenShouldReturnsAFailure() = runTest {
        // given
        val (arrangement, useCase) = arrangement
            .withFailingUserRetrieve()
            .withSuccessfulTeamRetrieve()
            .arrange()

        // when
        val result = useCase(userId)

        // then
        assertEquals(GetUserInfoResult.Failure, result)

        with(arrangement) {
            coVerify {
                userRepository.getKnownUser(eq(userId))
            }.wasInvoked(once)

            coVerify {
                userRepository.userById(eq(userId))
            }.wasInvoked(once)
        }
    }

    @Test
    fun givenAUserWithNoTeam_WhenGettingDetails_thenShouldReturnSuccessResultAndDoNotRetrieveTeam() = runTest {
        // given
        val (arrangement, useCase) = arrangement
            .withSuccessfulUserRetrieve(hasTeam = false)
            .arrange()

        // when
        val result = useCase(userId)

        // then
        assertEquals(OTHER.copy(teamId = null), (result as GetUserInfoResult.Success).otherUser)

        with(arrangement) {
            coVerify {
                userRepository.getKnownUser(eq(userId))
            }.wasInvoked(once)

            coVerify {
                teamRepository.getTeam(any())
            }.wasNotInvoked()
        }
    }

    @Test
    fun givenAInternalUserWithTeamNotExistingLocally_WhenGettingDetails_thenShouldReturnSuccessResultAndGetRemoteUserTeam() = runTest {
        // given
        val (arrangement, useCase) = arrangement
            .withSuccessfulUserRetrieve(userType = UserType.INTERNAL)
            .withSuccessfulTeamRetrieve(localTeamPresent = false)
            .arrange()

        // when
        val result = useCase(userId)

        // then
        assertEquals(OTHER.copy(userType = UserType.INTERNAL), (result as GetUserInfoResult.Success).otherUser)

        with(arrangement) {
            coVerify {
                userRepository.getKnownUser(eq(userId))
            }.wasInvoked(once)

            coVerify {
                teamRepository.getTeam(any())
            }.wasInvoked(once)

            coVerify {
                teamRepository.fetchTeamById(any())
            }.wasInvoked(once)
        }
    }

    @Test
    fun givenAUserWithTeamNotExistingLocallyAndFetchingUserInfoFails_WhenGettingDetails_ThenPropagateTheFailureAndDoNotGetTheTeamDetails() =
        runTest {
            // given
            val (arrangement, useCase) = arrangement
                .withFailingUserRetrieve()
                .withFailingTeamRetrieve()
                .arrange()
            // when
            val result = useCase(userId)

            // then
            assertIs<GetUserInfoResult.Failure>(result)

            with(arrangement) {
                coVerify {
                    userRepository.getKnownUser(eq(userId))
                }.wasInvoked(once)

                coVerify {
                    userRepository.userById(any())
                }.wasInvoked(once)

                coVerify {
                    teamRepository.getTeam(any())
                }.wasNotInvoked()

                coVerify {
                    teamRepository.fetchTeamById(any())
                }.wasNotInvoked()
            }
        }

    @Test
    fun givenAInternalUserWithTeamNotExistingLocallyAndFetchingTeamFails_WhenGettingDetails_ThenPropagateTheFailure() = runTest {
        // given

        val (arrangement, useCase) = arrangement
            .withSuccessfulUserRetrieve(localUserPresent = true, userType = UserType.INTERNAL)
            .withFailingTeamRetrieve()
            .arrange()

        // when
        val result = useCase(userId)

        // then
        assertIs<GetUserInfoResult.Failure>(result)

        with(arrangement) {
            coVerify {
                userRepository.getKnownUser(eq(userId))
            }.wasInvoked(once)

            coVerify {
                teamRepository.getTeam(any())
            }.wasInvoked(once)

            coVerify {
                teamRepository.fetchTeamById(any())
            }.wasInvoked(once)
        }
    }

    private companion object {
        val userId = UserId("some_user", "some_domain")
    }
}

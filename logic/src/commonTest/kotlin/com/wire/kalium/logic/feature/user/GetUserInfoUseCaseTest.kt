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
import com.wire.kalium.logic.data.user.type.UserTypeInfo
import com.wire.kalium.logic.framework.TestUser.OTHER
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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
            verifySuspend(VerifyMode.exactly(1)) {
                userRepository.getKnownUser(eq(userId))
            }

            verifySuspend(VerifyMode.exactly(1)) {
                userRepository.userById(eq(userId))
            }
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
            verifySuspend(VerifyMode.exactly(1)) {
                userRepository.getKnownUser(eq(userId))
            }

            verifySuspend(VerifyMode.not) {
                userRepository.userById(eq(userId))
            }
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
            verifySuspend(VerifyMode.exactly(1)) {
                userRepository.getKnownUser(eq(userId))
            }

            verifySuspend(VerifyMode.exactly(1)) {
                userRepository.userById(eq(userId))
            }
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
            verifySuspend(VerifyMode.exactly(1)) {
                userRepository.getKnownUser(eq(userId))
            }

            verifySuspend(VerifyMode.not) {
                teamRepository.getTeam(any())
            }
        }
    }

    @Test
    fun givenAInternalUserWithTeamNotExistingLocally_WhenGettingDetails_thenShouldReturnSuccessResultAndGetRemoteUserTeam() = runTest {
        // given
        val (arrangement, useCase) = arrangement
            .withSuccessfulUserRetrieve(userType = UserTypeInfo.Regular(UserType.INTERNAL))
            .withSuccessfulTeamRetrieve(localTeamPresent = false)
            .arrange()

        // when
        val result = useCase(userId)

        // then
        assertEquals(OTHER.copy(userType = UserTypeInfo.Regular(UserType.INTERNAL)), (result as GetUserInfoResult.Success).otherUser)

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) {
                userRepository.getKnownUser(eq(userId))
            }

            verifySuspend(VerifyMode.exactly(1)) {
                teamRepository.getTeam(any())
            }

            verifySuspend(VerifyMode.exactly(1)) {
                teamRepository.fetchTeamById(any())
            }
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
                verifySuspend(VerifyMode.exactly(1)) {
                    userRepository.getKnownUser(eq(userId))
                }

                verifySuspend(VerifyMode.exactly(1)) {
                    userRepository.userById(any())
                }

                verifySuspend(VerifyMode.not) {
                    teamRepository.getTeam(any())
                }

                verifySuspend(VerifyMode.not) {
                    teamRepository.fetchTeamById(any())
                }
            }
        }

    @Test
    fun givenAInternalUserWithTeamNotExistingLocallyAndFetchingTeamFails_WhenGettingDetails_ThenPropagateTheFailure() = runTest {
        // given

        val (arrangement, useCase) = arrangement
            .withSuccessfulUserRetrieve(localUserPresent = true, userType = UserTypeInfo.Regular(UserType.INTERNAL))
            .withFailingTeamRetrieve()
            .arrange()

        // when
        val result = useCase(userId)

        // then
        assertIs<GetUserInfoResult.Failure>(result)

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) {
                userRepository.getKnownUser(eq(userId))
            }

            verifySuspend(VerifyMode.exactly(1)) {
                teamRepository.getTeam(any())
            }

            verifySuspend(VerifyMode.exactly(1)) {
                teamRepository.fetchTeamById(any())
            }
        }
    }

    private companion object {
        val userId = UserId("some_user", "some_domain")
    }
}

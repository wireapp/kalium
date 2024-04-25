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
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.framework.TestUser
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveSelfUserWithTeamUseCaseTest {

    private val arrangement = Arrangement()

    @Test
    fun whenUserHasNoTeam_thenValidDataIsPassedForward() = runTest {
        val expected = TestUser.SELF to null
        val (_, useCase) = arrangement
            .withSelfUserWithTeam(expected)
            .arrange()

        useCase().test {
            assertEquals(expected, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun whenUserHasTeam_thenValidDataIsPassedForward() = runTest {
        val expected = TestUser.SELF to TestTeam.TEAM
        val (_, useCase) = arrangement
            .withSelfUserWithTeam(expected)
            .arrange()

        useCase().test {
            assertEquals(expected, awaitItem())
            awaitComplete()
        }
    }

    private class Arrangement {
        @Mock
        val userRepository: UserRepository = mock(UserRepository::class)

        suspend fun withSelfUserWithTeam(result: Pair<SelfUser, Team?>) = apply {
            coEvery {
                userRepository.observeSelfUserWithTeam()
            }.returns(flowOf(result))
        }

        fun arrange(): Pair<Arrangement, ObserveSelfUserWithTeamUseCase> {
            return this to ObserveSelfUserWithTeamUseCaseImpl(userRepository)
        }
    }
}

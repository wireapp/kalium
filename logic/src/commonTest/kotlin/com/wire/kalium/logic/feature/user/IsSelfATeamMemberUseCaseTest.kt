/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.feature.SelfTeamIdProvider
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsSelfATeamMemberUseCaseTest {

    @Test
    fun givenSelfIsTeamMember_thenReturnTrue() = runTest {
        val (arrangement, isSelfTeamMember) = Arrangement()
            .withSelfTeamId(Either.Right(TeamId("gg")))
            .arrange()

        isSelfTeamMember().also { actual ->
            assertTrue(actual)
        }

        verify(arrangement.selfTeamIdProvider)
            .suspendFunction(arrangement.selfTeamIdProvider::invoke)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSelfIsNotTeamMember_thenReturnTrue() = runTest {
        val (arrangement, isSelfTeamMember) = Arrangement()
            .withSelfTeamId(Either.Right(null))
            .arrange()

        isSelfTeamMember().also { actual ->
            assertFalse(actual)
        }

        verify(arrangement.selfTeamIdProvider)
            .suspendFunction(arrangement.selfTeamIdProvider::invoke)
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val selfTeamIdProvider: SelfTeamIdProvider = mock(SelfTeamIdProvider::class)

        private val isSelfATeamMember: IsSelfATeamMemberUseCase = IsSelfATeamMemberUseCase(selfTeamIdProvider)
        suspend fun withSelfTeamId(result: Either<CoreFailure, TeamId?>) = apply {
            given(selfTeamIdProvider).coroutine { invoke() }.then { result }
        }

        fun arrange() = this to isSelfATeamMember
    }
}

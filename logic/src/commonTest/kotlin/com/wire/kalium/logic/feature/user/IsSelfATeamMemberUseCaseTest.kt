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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.common.functional.Either
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsSelfATeamMemberUseCaseTest {

    @Test
    fun givenSelfIsTeamMember_thenReturnTrue() = runTest {
        val (arrangement, isSelfTeamMember) = Arrangement()
            .withLastSlowSyncCompletionInstant(Instant.DISTANT_PAST)
            .withSelfTeamId(Either.Right(TeamId("gg")))
            .arrange()

        isSelfTeamMember().also { actual ->
            assertTrue(actual)
        }

        coVerify {
            arrangement.selfTeamIdProvider.invoke()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenSelfIsNotTeamMember_thenReturnTrue() = runTest {
        val (arrangement, isSelfTeamMember) = Arrangement()
            .withLastSlowSyncCompletionInstant(Instant.DISTANT_PAST)
            .withSelfTeamId(Either.Right(null))
            .arrange()

        isSelfTeamMember().also { actual ->
            assertFalse(actual)
        }

        coVerify {
            arrangement.selfTeamIdProvider.invoke()
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val selfTeamIdProvider: SelfTeamIdProvider = mock(SelfTeamIdProvider::class)

        @Mock
        val slowSyncRepository: SlowSyncRepository = mock(SlowSyncRepository::class)

        private val isSelfATeamMember: IsSelfATeamMemberUseCaseImpl = IsSelfATeamMemberUseCaseImpl(
            selfTeamIdProvider = selfTeamIdProvider,
            slowSyncRepository = slowSyncRepository
        )

        suspend fun withLastSlowSyncCompletionInstant(result: Instant?) = apply {
            coEvery { slowSyncRepository.observeLastSlowSyncCompletionInstant() }.returns(flowOf(result))
        }

        suspend fun withSelfTeamId(result: Either<CoreFailure, TeamId?>) = apply {
            coEvery {
                selfTeamIdProvider.invoke()
            }.returns(result)
        }

        fun arrange() = this to isSelfATeamMember
    }
}

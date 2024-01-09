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

package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.team.TeamRole
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class TeamEventReceiverTest {

    @Test
    fun givenTeamUpdateEvent_repoIsInvoked() = runTest {
        val event = TestEvent.teamUpdated()
        val (arrangement, eventReceiver) = Arrangement()
            .withUpdateTeamSuccess()
            .arrange()

        eventReceiver.onEvent(event)

        verify(arrangement.teamRepository)
            .suspendFunction(arrangement.teamRepository::updateTeam)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberUpdateEvent_RepoIsInvoked() = runTest {
        val event = TestEvent.teamMemberUpdate(permissionCode = TeamRole.Member.value)
        val (arrangement, eventReceiver) = Arrangement()
            .withMemberUpdateSuccess()
            .arrange()

        eventReceiver.onEvent(event)

        verify(arrangement.teamRepository)
            .suspendFunction(arrangement.teamRepository::updateMemberRole)
            .with(any(), any(), any())
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val teamRepository = mock(classOf<TeamRepository>())

        private val teamEventReceiver: TeamEventReceiver = TeamEventReceiverImpl(
            teamRepository
        )

        fun withUpdateTeamSuccess() = apply {
            given(teamRepository).suspendFunction(teamRepository::updateTeam).whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withMemberUpdateSuccess() = apply {
            given(teamRepository).suspendFunction(teamRepository::updateMemberRole)
                .whenInvokedWith(any(), any(), any()).thenReturn(Either.Right(Unit))
        }

        fun arrange() = this to teamEventReceiver
    }
}

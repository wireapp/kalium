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

package com.wire.kalium.logic.feature.team

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DeleteTeamConversationUseCaseTest {

    @Test
    fun givenAConversationId_whenInvokingADeleteConversation_thenShouldDelegateTheCallAndReturnASuccessResult() = runTest {
        val (arrangement, deleteTeamConversation) = Arrangement()
            .withGetSelfTeam()
            .withSuccessApiDeletingConversation()
            .withSuccessDeletingConversationLocally()
            .arrange()

        val result = deleteTeamConversation(TestConversation.ID)

        assertEquals(Result.Success::class, result::class)
        verify(arrangement.selfTeamIdProvider)
            .suspendFunction(arrangement.selfTeamIdProvider::invoke)
            .wasInvoked(once)
        verify(arrangement.teamRepository)
            .suspendFunction(arrangement.teamRepository::deleteConversation)
            .with(eq(TestConversation.ID), eq(TeamId(TestTeam.TEAM.id)))
            .wasInvoked(once)
        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::deleteConversation)
            .with(eq(TestConversation.ID))
            .wasInvoked(once)
    }

    @Test
    fun givenAConversationId_whenInvokingADeleteConversationAndAnError_thenShouldDelegateTheCallAndReturnAFailureResult() = runTest {
        val (arrangement, deleteTeamConversation) = Arrangement()
            .withGetSelfTeam()
            .withApiErrorDeletingConversation()
            .arrange()

        val result = deleteTeamConversation(TestConversation.ID)

        assertEquals(Result.Failure.GenericFailure::class, result::class)
        verify(arrangement.selfTeamIdProvider)
            .suspendFunction(arrangement.selfTeamIdProvider::invoke)
            .wasInvoked(once)
        verify(arrangement.teamRepository)
            .suspendFunction(arrangement.teamRepository::deleteConversation)
            .with(eq(TestConversation.ID), eq(TeamId(TestTeam.TEAM.id)))
            .wasInvoked(once)
        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::deleteConversation)
            .with(eq(TestConversation.ID))
            .wasNotInvoked()
    }

    @Test
    fun givenAConversationId_whenInvokingADeleteConversationAndAnNoTeam_thenShouldDelegateTheCallAndReturnAFailureResult() = runTest {
        val (arrangement, deleteTeamConversation) = Arrangement()
            .withGetSelfTeam(null)
            .withApiErrorDeletingConversation()
            .arrange()

        val result = deleteTeamConversation(TestConversation.ID)

        assertEquals(Result.Failure.NoTeamFailure::class, result::class)
        verify(arrangement.selfTeamIdProvider)
            .suspendFunction(arrangement.selfTeamIdProvider::invoke)
            .wasInvoked(once)
        verify(arrangement.teamRepository)
            .suspendFunction(arrangement.teamRepository::deleteConversation)
            .with(eq(TestConversation.ID), eq(TestTeam.TEAM.id))
            .wasNotInvoked()
        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::deleteConversation)
            .with(eq(TestConversation.ID))
            .wasNotInvoked()
    }

    private class Arrangement {

        var deleteTeamConversation: DeleteTeamConversationUseCase

        @Mock
        val selfTeamIdProvider: SelfTeamIdProvider = mock(SelfTeamIdProvider::class)

        @Mock
        val teamRepository: TeamRepository = mock(TeamRepository::class)

        @Mock
        val conversationRepository: ConversationRepository = mock(ConversationRepository::class)

        init {
            deleteTeamConversation = DeleteTeamConversationUseCaseImpl(selfTeamIdProvider, teamRepository, conversationRepository)
        }

        fun withGetSelfTeam(team: Team? = TestTeam.TEAM) = apply {
            val result = team?.id?.let {
                TeamId(it)
            }
            given(selfTeamIdProvider)
                .suspendFunction(selfTeamIdProvider::invoke)
                .whenInvoked()
                .thenReturn(Either.Right(result))
        }

        fun withApiErrorDeletingConversation() = apply {
            given(teamRepository)
                .suspendFunction(teamRepository::deleteConversation)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Left(CoreFailure.Unknown(RuntimeException("some error"))))
        }

        fun withSuccessApiDeletingConversation() = apply {
            given(teamRepository)
                .suspendFunction(teamRepository::deleteConversation)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
        }

        fun withSuccessDeletingConversationLocally() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::deleteConversation)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun arrange() = this to deleteTeamConversation
    }

}

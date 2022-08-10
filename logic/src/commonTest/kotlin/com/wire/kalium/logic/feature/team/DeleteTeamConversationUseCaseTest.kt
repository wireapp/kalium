package com.wire.kalium.logic.feature.team

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.team.TeamRepository
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DeleteTeamConversationUseCaseTest {

    @Test
    fun givenAConversationId_whenInvokingADeleteConversation_thenShouldDelegateTheCallAndReturnASuccessResult() = runTest {
        val (arrangement, deleteTeamConversation) = Arrangement()
            .withGetSelfTeam()
            .withSuccessApiDeletingConversation()
            .arrange()

        val result = deleteTeamConversation(TestConversation.ID)

        assertEquals(Result.Success::class, result::class)
        verify(arrangement.getSelfTeamUseCase)
            .suspendFunction(arrangement.getSelfTeamUseCase::invoke)
            .wasInvoked(once)
        verify(arrangement.teamRepository)
            .suspendFunction(arrangement.teamRepository::deleteConversation)
            .with(eq(TestConversation.ID), eq(TestTeam.TEAM.id))
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
        verify(arrangement.getSelfTeamUseCase)
            .suspendFunction(arrangement.getSelfTeamUseCase::invoke)
            .wasInvoked(once)
        verify(arrangement.teamRepository)
            .suspendFunction(arrangement.teamRepository::deleteConversation)
            .with(eq(TestConversation.ID), eq(TestTeam.TEAM.id))
            .wasInvoked(once)
    }

    @Test
    fun givenAConversationId_whenInvokingADeleteConversationAndAnNoTeam_thenShouldDelegateTheCallAndReturnAFailureResult() = runTest {
        val (arrangement, deleteTeamConversation) = Arrangement()
            .withGetSelfTeam(null)
            .withApiErrorDeletingConversation()
            .arrange()

        val result = deleteTeamConversation(TestConversation.ID)

        assertEquals(Result.Failure.NoTeamFailure::class, result::class)
        verify(arrangement.getSelfTeamUseCase)
            .suspendFunction(arrangement.getSelfTeamUseCase::invoke)
            .wasInvoked(once)
        verify(arrangement.teamRepository)
            .suspendFunction(arrangement.teamRepository::deleteConversation)
            .with(eq(TestConversation.ID), eq(TestTeam.TEAM.id))
            .wasNotInvoked()
    }

    private class Arrangement {

        var deleteTeamConversation: DeleteTeamConversationUseCase

        @Mock
        val getSelfTeamUseCase: GetSelfTeamUseCase = mock(GetSelfTeamUseCase::class)

        @Mock
        val teamRepository: TeamRepository = mock(TeamRepository::class)

        init {
            deleteTeamConversation = DeleteTeamConversationUseCaseImpl(getSelfTeamUseCase, teamRepository)
        }

        fun withGetSelfTeam(team: Team? = TestTeam.TEAM) = apply {
            given(getSelfTeamUseCase)
                .suspendFunction(getSelfTeamUseCase::invoke)
                .whenInvoked()
                .thenReturn(flowOf(team))
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
        
        fun arrange() = this to deleteTeamConversation
    }

}

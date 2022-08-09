package com.wire.kalium.logic.feature.team

import com.wire.kalium.logic.CoreFailure
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
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DeleteTeamConversationUseCaseTest {

    lateinit var deleteTeamConversation: DeleteTeamConversationUseCase

    @Mock
    private val getSelfTeamUseCase: GetSelfTeamUseCase = mock(GetSelfTeamUseCase::class)

    @Mock
    private val teamRepository: TeamRepository = mock(TeamRepository::class)

    @BeforeTest
    fun setup() {
        deleteTeamConversation = DeleteTeamConversationUseCaseImpl(getSelfTeamUseCase, teamRepository)
    }

    @Test
    fun givenAConversationId_whenInvokingADeleteConversation_thenShouldDelegateTheCallAndReturnASuccessResult() = runTest {
        given(getSelfTeamUseCase)
            .suspendFunction(getSelfTeamUseCase::invoke)
            .whenInvoked()
            .thenReturn(flowOf(TestTeam.TEAM))
        given(teamRepository)
            .suspendFunction(teamRepository::deleteConversation)
            .whenInvokedWith(any(), any())
            .thenReturn(Either.Right(Unit))


        val result = deleteTeamConversation(TestConversation.ID)

        assertEquals(Result.Success::class, result::class)
        verify(getSelfTeamUseCase)
            .suspendFunction(getSelfTeamUseCase::invoke)
            .wasInvoked(once)
        verify(teamRepository)
            .suspendFunction(teamRepository::deleteConversation)
            .with(eq(TestConversation.ID), eq(TestTeam.TEAM.id))
            .wasInvoked(once)
    }

    @Test
    fun givenAConversationId_whenInvokingADeleteConversationAndAnError_thenShouldDelegateTheCallAndReturnAFailureResult() = runTest {
        given(getSelfTeamUseCase)
            .suspendFunction(getSelfTeamUseCase::invoke)
            .whenInvoked()
            .thenReturn(flowOf(TestTeam.TEAM))
        given(teamRepository)
            .suspendFunction(teamRepository::deleteConversation)
            .whenInvokedWith(any(), any())
            .thenReturn(Either.Left(CoreFailure.Unknown(RuntimeException("some error"))))


        val result = deleteTeamConversation(TestConversation.ID)

        assertEquals(Result.Failure::class, result::class)
        verify(getSelfTeamUseCase)
            .suspendFunction(getSelfTeamUseCase::invoke)
            .wasInvoked(once)
        verify(teamRepository)
            .suspendFunction(teamRepository::deleteConversation)
            .with(eq(TestConversation.ID), eq(TestTeam.TEAM.id))
            .wasInvoked(once)
    }

}

package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.team.TeamRole
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
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
    fun givenMemberJoinEvent_repoIsInvoked() = runTest {
        val event = TestEvent.teamMemberJoin()
        val (arrangement, eventReceiver) = Arrangement()
            .withMemberJoinSuccess()
            .arrange()

        eventReceiver.onEvent(event)

        verify(arrangement.teamRepository)
            .suspendFunction(arrangement.teamRepository::fetchTeamMember)
            .with(any(), any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberLeaveEvent_RepoIsInvoked() = runTest {
        val event = TestEvent.teamMemberLeave()
        val (arrangement, eventReceiver) = Arrangement()
            .withMemberLeaveSuccess()
            .arrange()

        eventReceiver.onEvent(event)

        verify(arrangement.teamRepository)
            .suspendFunction(arrangement.teamRepository::removeTeamMember)
            .with(any(), any())
            .wasInvoked(exactly = once)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::deleteUserFromConversations)
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

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        private val userEventReceiver: TeamEventReceiver = TeamEventReceiverImpl(
            teamRepository, conversationRepository,
            TestUser.USER_ID
        )

        fun withUpdateTeamSuccess() = apply {
            given(teamRepository).suspendFunction(teamRepository::updateTeam).whenInvokedWith(any()).thenReturn(Either.Right(Unit))
        }

        fun withMemberJoinSuccess() = apply {
            given(teamRepository).suspendFunction(teamRepository::fetchTeamMember)
                .whenInvokedWith(any(), any()).thenReturn(Either.Right(Unit))
        }

        fun withMemberLeaveSuccess() = apply {
            given(teamRepository).suspendFunction(teamRepository::removeTeamMember)
                .whenInvokedWith(any(), any()).thenReturn(Either.Right(Unit))
            given(conversationRepository).suspendFunction(conversationRepository::deleteUserFromConversations)
                .whenInvokedWith(any()).thenReturn(Either.Right(Unit))
        }

        fun withMemberUpdateSuccess() = apply {
            given(teamRepository).suspendFunction(teamRepository::updateMemberRole)
                .whenInvokedWith(any(), any(), any()).thenReturn(Either.Right(Unit))
        }

        fun arrange() = this to userEventReceiver
    }
}

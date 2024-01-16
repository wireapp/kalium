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

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.team.TeamRole
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangementImpl
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
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
    fun givenMemberLeaveEvent_RepoAndPersisMessageAreInvoked() = runTest {
        val event = TestEvent.teamMemberLeave()
        val (arrangement, eventReceiver) = Arrangement()
            .arrange {
                withMarkUserAsDeletedAndRemoveFromGroupConversationsSuccess(
                    listOf(TestConversation.ID)
                )
                withConversationsByUserId(listOf(TestConversation.CONVERSATION))
                withPersistMessageSuccess()
            }

        eventReceiver.onEvent(event)

        verify(arrangement.persistMessageUseCase)
            .suspendFunction(arrangement.persistMessageUseCase::invoke)
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

    private class Arrangement : UserRepositoryArrangement by UserRepositoryArrangementImpl() {
        @Mock
        val teamRepository = mock(classOf<TeamRepository>())

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val persistMessageUseCase = mock(classOf<PersistMessageUseCase>())

        private val teamEventReceiver: TeamEventReceiver = TeamEventReceiverImpl(
            teamRepository, conversationRepository, userRepository, persistMessageUseCase,
            TestUser.USER_ID
        )

        init {
            apply {
                withGetKnownUserReturning(flowOf(TestUser.OTHER))
            }
        }

        fun withUpdateTeamSuccess() = apply {
            given(teamRepository).suspendFunction(teamRepository::updateTeam).whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withMemberUpdateSuccess() = apply {
            given(teamRepository).suspendFunction(teamRepository::updateMemberRole)
                .whenInvokedWith(any(), any(), any()).thenReturn(Either.Right(Unit))
        }

        fun withConversationsByUserId(conversationIds: List<Conversation>) = apply {
            given(conversationRepository).suspendFunction(conversationRepository::getConversationsByUserId)
                .whenInvokedWith(any()).thenReturn(Either.Right(conversationIds))
        }

        fun withPersistMessageSuccess() = apply {
            given(persistMessageUseCase).suspendFunction(persistMessageUseCase::invoke)
                .whenInvokedWith(any()).thenReturn(Either.Right(Unit))
        }

        fun arrange(block: Arrangement.() -> Unit = { }) = apply(block)
            .let {
                this to teamEventReceiver
            }
    }
}

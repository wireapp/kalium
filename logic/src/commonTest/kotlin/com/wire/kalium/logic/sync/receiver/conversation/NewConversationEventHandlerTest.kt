package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.SelfTeamIdProvider
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.wasInTheLastSecond
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.toInstant
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewConversationEventHandlerTest {

    @Test
    fun givenNewConversationOriginatedFromEvent_whenHandlingIt_thenPersistConversationShouldBeCalled() = runTest {
        val event = Event.Conversation.NewConversation(
            id = "eventId",
            conversationId = TestConversation.ID,
            transient = false,
            timestampIso = "timestamp",
            conversation = TestConversation.CONVERSATION_RESPONSE,
        )
        val members = event.conversation.members.otherMembers.map { it.id.toModel() }.toSet()
        val teamIdValue = "teamId"
        val teamId = TeamId(teamIdValue)

        val (arrangement, eventHandler) = Arrangement()
            .withUpdateConversationModifiedDateReturning(Either.Right(Unit))
            .withPersistingConversations(Either.Right(Unit))
            .withFetchUsersIfUnknownIds(members)
            .withSelfUserTeamId(Either.Right(teamId))
            .arrange()

        eventHandler.handle(event)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::persistConversations)
            .with(eq(listOf(event.conversation)), eq(teamIdValue), eq(true))
            .wasInvoked(exactly = once)

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::fetchUsersIfUnknownByIds)
            .with(eq(members))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenNewConversationEvent_whenHandlingIt_thenConversationLastModifiedShouldBeUpdated() = runTest {
        val event = Event.Conversation.NewConversation(
            id = "eventId",
            conversationId = TestConversation.ID,
            false,
            timestampIso = "timestamp",
            conversation = TestConversation.CONVERSATION_RESPONSE
        )

        val members = event.conversation.members.otherMembers.map { it.id.toModel() }.toSet()
        val teamId = TestTeam.TEAM_ID

        val (arrangement, eventHandler) = Arrangement()
            .withUpdateConversationModifiedDateReturning(Either.Right(Unit))
            .withPersistingConversations(Either.Right(Unit))
            .withFetchUsersIfUnknownIds(members)
            .withSelfUserTeamId(Either.Right(teamId))
            .arrange()

        eventHandler.handle(event)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateConversationModifiedDate)
            .with(eq(event.conversationId), matching { it.toInstant().wasInTheLastSecond })
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val userRepository = mock(classOf<UserRepository>())

        @Mock
        val selfTeamIdProvider = mock(classOf<SelfTeamIdProvider>())

        private val newConversationEventHandler: NewConversationEventHandler = NewConversationEventHandlerImpl(
            conversationRepository,
            userRepository,
            selfTeamIdProvider
        )

        fun withUpdateConversationModifiedDateReturning(result: Either<StorageFailure, Unit>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateConversationModifiedDate)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
        }

        fun withPersistingConversations(result: Either<StorageFailure, Unit>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::persistConversations)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(result)
        }

        suspend fun withFetchUsersIfUnknownIds(members: Set<QualifiedID>) = apply {
            given(userRepository)
                .suspendFunction(userRepository::fetchUsersIfUnknownByIds)
                .whenInvokedWith(eq(members))
                .thenReturn(Either.Right(Unit))
        }

        fun withSelfUserTeamId(either: Either<CoreFailure, TeamId?>) = apply {
            given(selfTeamIdProvider)
                .suspendFunction(selfTeamIdProvider::invoke)
                .whenInvoked()
                .then { either }
        }

        fun arrange() = this to newConversationEventHandler
    }

}

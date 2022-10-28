package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.framework.TestConversation
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
    fun givenNewConversationEvent_whenHandlingIt_thenInsertConversationFromEventShouldBeCalled() = runTest {
        val event = Event.Conversation.NewConversation(
            id = "eventId",
            conversationId = TestConversation.ID,
            timestampIso = "timestamp",
            conversation = TestConversation.CONVERSATION_RESPONSE,
        )
        val members = event.conversation.members.otherMembers.map { MapperProvider.idMapper().fromApiModel(it.id) }.toSet()

        val (arrangement, eventHandler) = Arrangement()
            .withUpdateConversationModifiedDateReturning(Either.Right(Unit))
            .withInsertConversationFromEventReturning(Either.Right(Unit))
            .withFetchUsersIfUnknownIds(members)
            .arrange()

        eventHandler.handle(event)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::insertConversationFromEvent)
            .with(eq(event))
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
            timestampIso = "timestamp",
            conversation = TestConversation.CONVERSATION_RESPONSE
        )

        val members = event.conversation.members.otherMembers.map { MapperProvider.idMapper().fromApiModel(it.id) }.toSet()

        val (arrangement, eventHandler) = Arrangement()
            .withUpdateConversationModifiedDateReturning(Either.Right(Unit))
            .withInsertConversationFromEventReturning(Either.Right(Unit))
            .withFetchUsersIfUnknownIds(members)
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

        private val newConversationEventHandler: NewConversationEventHandler = NewConversationEventHandlerImpl(
            conversationRepository,
            userRepository
        )

        fun withUpdateConversationModifiedDateReturning(result: Either<StorageFailure, Unit>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateConversationModifiedDate)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
        }

        fun withInsertConversationFromEventReturning(result: Either<StorageFailure, Unit>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::insertConversationFromEvent)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        suspend fun withFetchUsersIfUnknownIds(members: Set<QualifiedID>) = apply {
            given(userRepository)
                .suspendFunction(userRepository::fetchUsersIfUnknownByIds)
                .whenInvokedWith(eq(members))
                .thenReturn(Either.Right(Unit))
        }

        fun arrange() = this to newConversationEventHandler
    }

}

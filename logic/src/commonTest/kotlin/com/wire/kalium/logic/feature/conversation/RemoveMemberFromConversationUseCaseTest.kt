package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MemberChangeResult
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class RemoveMemberFromConversationUseCaseTest {

    @Test
    fun givenMemberAndConversation_WhenRemoveMemberIsSuccessful_ThenReturnSuccessAndPersistMessage() = runTest {
        val (arrangement, removeMemberUseCase) = Arrangement()
            .withRemoveMemberGroupIs(Either.Right(MemberChangeResult.Changed("2022-01-01T00:00:00.000Z")))
            .withPersistMessage(Either.Right(Unit))
            .arrange()

        val result = removeMemberUseCase(TestConversation.ID, TestConversation.USER_1)

        assertIs<RemoveMemberFromConversationUseCase.Result.Success>(result)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::deleteMember)
            .with(eq(TestConversation.USER_1), eq(TestConversation.ID))
            .wasInvoked(exactly = once)

        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberAndConversation_WhenRemoveMemberIsSuccessfulButUnchanged_ThenReturnSuccessAndDoNotPersistMessage() = runTest {
        val (arrangement, removeMemberUseCase) = Arrangement()
            .withRemoveMemberGroupIs(Either.Right(MemberChangeResult.Unchanged))
            .arrange()

        val result = removeMemberUseCase(TestConversation.ID, TestConversation.USER_1)

        assertIs<RemoveMemberFromConversationUseCase.Result.Success>(result)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::deleteMember)
            .with(eq(TestConversation.USER_1), eq(TestConversation.ID))
            .wasInvoked(exactly = once)

        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenMemberAndConversation_WhenRemoveMemberFailed_ThenReturnFailure() = runTest {
        val (arrangement, removeMemberUseCase) = Arrangement()
            .withRemoveMemberGroupIs(Either.Left(StorageFailure.DataNotFound))
            .arrange()

        val result = removeMemberUseCase(TestConversation.ID, TestConversation.USER_1)
        assertIs<RemoveMemberFromConversationUseCase.Result.Failure>(result)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::deleteMember)
            .with(eq(TestConversation.USER_1), eq(TestConversation.ID))
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val persistMessage = mock(classOf<PersistMessageUseCase>())

        var selfUserId = UserId("my-own-user-id", "my-domain")

        private val removeMemberUseCase = RemoveMemberFromConversationUseCaseImpl(
            conversationRepository,
            selfUserId,
            persistMessage
        )

        fun withRemoveMemberGroupIs(either: Either<CoreFailure, MemberChangeResult>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::deleteMember)
                .whenInvokedWith(any(), any())
                .thenReturn(either)
        }

        fun withPersistMessage(either: Either<CoreFailure, Unit>) = apply {
            given(persistMessage)
                .suspendFunction(persistMessage::invoke)
                .whenInvokedWith(any())
                .thenReturn(either)
        }

        fun arrange() = this to removeMemberUseCase
    }
}

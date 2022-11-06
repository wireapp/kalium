package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser.USER_ID
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
class RenameConversationUseCaseTest {

    @Test
    fun givenAConversation_WhenChangingNameIsSuccessful_ThenReturnSuccess() = runTest {
        val (arrangement, renameConversation) = Arrangement()
            .withRenameConversationIs(Either.Right(Unit))
            .arrange()

        val result = renameConversation(TestConversation.ID, "new_name")

        assertIs<RenamingResult.Success>(result)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::changeConversationName)
            .with(eq(TestConversation.ID), eq("new_name"))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversation_WhenChangingNameFails_ThenReturnFailure() = runTest {
        val (arrangement, renameConversation) = Arrangement()
            .withRenameConversationIs(Either.Left(CoreFailure.Unknown(RuntimeException("Error!"))))
            .arrange()

        val result = renameConversation(TestConversation.ID, "new_name")

        assertIs<RenamingResult.Failure>(result)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::changeConversationName)
            .with(eq(TestConversation.ID), eq("new_name"))
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val persistMessage = mock(classOf<PersistMessageUseCase>())

        val selfUserId = USER_ID

        private val renameConversation = RenameConversationUseCaseImpl(conversationRepository, persistMessage, selfUserId)

        fun withRenameConversationIs(either: Either<CoreFailure, Unit>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::changeConversationName)
                .whenInvokedWith(any(), any())
                .thenReturn(either)
        }

        fun arrange() = this to renameConversation
    }
}

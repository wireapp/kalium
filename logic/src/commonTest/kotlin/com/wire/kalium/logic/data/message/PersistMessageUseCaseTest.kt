package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
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

class PersistMessageUseCaseTest {

    @Test
    fun givenMyMessage_whenPersistCalled_thenUpdateNotifiedDateIsCalled() = runTest {
        val message = TestMessage.TEXT_MESSAGE.copy(senderUserId = TestUser.USER_ID)
        val (arrangement, persistMessage) = Arrangement()
            .withPersistMessageResult(Either.Right(Unit))
            .arrange()

        persistMessage(message)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateConversationNotificationDate)
            .with(any(), any())
            .wasInvoked(exactly = once)
        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateConversationModifiedDate)
            .with(any(), any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMyDeleteMessage_whenPersistCalled_thenUpdateModifiedDateIsNotCalled() = runTest {
        val message = TestMessage.TEXT_MESSAGE.copy(senderUserId = TestUser.USER_ID, content = TEST_CONTENT_DELETE)
        val (arrangement, persistMessage) = Arrangement()
            .withPersistMessageResult(Either.Right(Unit))
            .arrange()

        persistMessage(message)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateConversationNotificationDate)
            .with(any(), any())
            .wasInvoked(exactly = once)
        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateConversationModifiedDate)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenEditMessage_whenPersistCalled_thenUpdateModifiedDateIsNotCalled() = runTest {
        val message = TestMessage.TEXT_MESSAGE.copy(
            senderUserId = TestUser.USER_ID.copy(value = "otherUser"),
            content = TEST_CONTENT_EDIT
        )
        val (arrangement, persistMessage) = Arrangement()
            .withPersistMessageResult(Either.Right(Unit))
            .arrange()

        persistMessage(message)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateConversationNotificationDate)
            .with(any(), any())
            .wasNotInvoked()
        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateConversationModifiedDate)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenMissedCallMessage_whenPersistCalled_thenUpdateModifiedDateIsNotCalled() = runTest {
        val message = TestMessage.MISSED_CALL_MESSAGE.copy(senderUserId = TestUser.USER_ID.copy(value = "otherUser"))
        val (arrangement, persistMessage) = Arrangement()
            .withPersistMessageResult(Either.Right(Unit))
            .arrange()

        persistMessage(message)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateConversationNotificationDate)
            .with(any(), any())
            .wasNotInvoked()
        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateConversationModifiedDate)
            .with(any(), any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMyMessage_whenPersistCalledAndFailed_thenUpdateNotifiedAndModifiedDateAreNotCalled() = runTest {
        val message = TestMessage.TEXT_MESSAGE.copy(senderUserId = TestUser.USER_ID)
        val (arrangement, persistMessage) = Arrangement()
            .withPersistMessageResult(Either.Left(CoreFailure.MissingClientRegistration))
            .arrange()

        persistMessage(message)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateConversationNotificationDate)
            .with(any(), any())
            .wasNotInvoked()
        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateConversationModifiedDate)
            .with(any(), any())
            .wasNotInvoked()
    }

    private class Arrangement {
        @Mock
        val messageRepository = mock(classOf<MessageRepository>())

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        val userId = TestUser.USER_ID

        private val persistMessage = PersistMessageUseCaseImpl(
            messageRepository,
            conversationRepository,
            userId
        )

        init {
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateConversationNotificationDate)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateConversationModifiedDate)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
        }

        fun withPersistMessageResult(result: Either<CoreFailure, Unit>) = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::persistMessage)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun arrange() = this to persistMessage
    }

    companion object {
        private val TEST_CONTENT_DELETE = MessageContent.DeleteMessage("message_id")
        private val TEST_CONTENT_EDIT = MessageContent.TextEdited("message_id", "newContent")
    }
}

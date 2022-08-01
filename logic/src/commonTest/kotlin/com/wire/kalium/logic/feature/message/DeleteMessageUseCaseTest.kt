package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.IdMapperImpl
import com.wire.kalium.logic.data.id.PlainId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.Mock
import io.mockative.anything
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteMessageUseCaseTest {
    @Test
    fun givenASentMessage_WhenDeleteForEveryIsTrue_TheGeneratedMessageShouldBeCorrect() = runTest {
        // given
        val deleteForEveryone = true

        val (arrangement, deleteMessageUseCase) = Arrangement()
            .withSendMessageSucceed()
            .withSelfUser(TestUser.SELF)
            .withCurrentClientIdIs(SELF_CLIENT_ID)
            .withMessageRepositoryMarkMessageAsDeletedSucceed()
            .withMessageByStatus(Message.Status.SENT)
            .arrange()

        // when
        deleteMessageUseCase(TEST_CONVERSATION_ID, TEST_MESSAGE_UUID, deleteForEveryone).shouldSucceed()

        // then
        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendMessage)
            .with(
                matching { message ->
                    message.conversationId == TEST_CONVERSATION_ID && message.content == deletedMessageContent
                }
            )
            .wasInvoked(exactly = once)
        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::markMessageAsDeleted)
            .with(eq(TEST_MESSAGE_UUID), eq(TEST_CONVERSATION_ID))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAFailedMessage_WhenItGetsDeletedForEveryone_TheMessageShouldBeDeleted() = runTest {
        // given
        val deleteForEveryone = true
        val (arrangement, deleteMessageUseCase) = Arrangement()
            .withSendMessageSucceed()
            .withSelfUser(TestUser.SELF)
            .withCurrentClientIdIs(SELF_CLIENT_ID)
            .withMessageRepositoryMarkMessageAsDeletedSucceed()
            .withMessageRepositoryDeleteMessageSucceed()
            .withMessageByStatus(Message.Status.FAILED)
            .arrange()

        // when
        deleteMessageUseCase(TEST_CONVERSATION_ID, TEST_MESSAGE_UUID, deleteForEveryone).shouldSucceed()

        // then
        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendMessage)
            .with(anything())
            .wasNotInvoked()
        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::markMessageAsDeleted)
            .with(anything(), anything())
            .wasNotInvoked()
        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::deleteMessage)
            .with(eq(TEST_MESSAGE_UUID), eq(TEST_CONVERSATION_ID))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenASentMessage_WhenDeleteForEveryoneIsFalse_TheGeneratedMessageShouldBeDeletedOnlyLocally() = runTest {
        // given
        val deleteForEveryone = false
        val (arrangement, deleteMessageUseCase) = Arrangement()
            .withSendMessageSucceed()
            .withSelfUser(TestUser.SELF)
            .withCurrentClientIdIs(SELF_CLIENT_ID)
            .withMessageRepositoryMarkMessageAsDeletedSucceed()
            .withMessageByStatus(Message.Status.SENT)
            .arrange()

        // when
        deleteMessageUseCase(TEST_CONVERSATION_ID, TEST_MESSAGE_UUID, deleteForEveryone).shouldSucceed()

        val deletedForMeContent = MessageContent.DeleteForMe(
            TEST_MESSAGE_UUID, TEST_CONVERSATION_ID.value,
            arrangement.idMapper.toProtoModel(
                TEST_CONVERSATION_ID
            )
        )

        // then
        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendMessage)
            .with(
                matching { message ->
                    message.conversationId == TestUser.SELF.id && message.content == deletedForMeContent
                }
            )
            .wasInvoked(exactly = once)

        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::markMessageAsDeleted)
            .with(eq(TEST_MESSAGE_UUID), eq(TEST_CONVERSATION_ID))
            .wasInvoked(exactly = once)

    }

    class Arrangement {

        @Mock
        val messageRepository: MessageRepository = mock(MessageRepository::class)

        @Mock
        val userRepository: UserRepository = mock(UserRepository::class)

        @Mock
        val clientRepository: ClientRepository = mock(ClientRepository::class)

        @Mock
        val messageSender: MessageSender = mock(MessageSender::class)

        val idMapper: IdMapper = IdMapperImpl()

        fun arrange() = this to DeleteMessageUseCase(
            messageRepository,
            userRepository,
            clientRepository,
            messageSender,
            idMapper
        )

        fun withSendMessageSucceed() = apply {
            given(messageSender)
                .suspendFunction(messageSender::sendMessage)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(Unit))
        }

        fun withSelfUser(selfUser: SelfUser) = apply {
            given(userRepository)
                .suspendFunction(userRepository::observeSelfUser)
                .whenInvoked()
                .thenReturn(flowOf(selfUser))
        }

        fun withCurrentClientIdIs(clientId: ClientId) = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::currentClientId)
                .whenInvoked()
                .then { Either.Right(clientId) }
        }

        fun withMessageRepositoryMarkMessageAsDeletedSucceed() = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::markMessageAsDeleted)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(Unit))
        }

        fun withMessageRepositoryDeleteMessageSucceed() = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::deleteMessage)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(Unit))
        }

        fun withMessageByStatus(status: Message.Status) = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::getMessageById)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(TestMessage.TEXT_MESSAGE(status)))
        }
    }

    companion object {
        val TEST_CONVERSATION_ID = TestConversation.ID
        const val TEST_MESSAGE_UUID = "messageUuid"
        val SELF_CLIENT_ID: ClientId = PlainId("client_self")
        val deletedMessageContent = MessageContent.DeleteMessage(TEST_MESSAGE_UUID)
    }
}

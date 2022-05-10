package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.PlainId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.Mock
import io.mockative.anything
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteMessageUseCaseTest {

    @Mock
    private val messageRepository: MessageRepository = mock(MessageRepository::class)

    @Mock
    val userRepository: UserRepository = mock(UserRepository::class)

    @Mock
    private val clientRepository: ClientRepository = mock(ClientRepository::class)

    @Mock
    private val syncManager = configure(mock(SyncManager::class)) { stubsUnitByDefault = true }

    @Mock
    private val messageSender: MessageSender = mock(MessageSender::class)

    private lateinit var deleteMessageUseCase: DeleteMessageUseCase

    @BeforeTest
    fun setup() {
        deleteMessageUseCase = DeleteMessageUseCase(
            messageRepository,
            userRepository,
            clientRepository,
            syncManager,
            messageSender
        )
    }

    @Test
    fun givenAMessage_WhenDeleteForEveryIsTrue_TheGeneratedMessageShouldBeCorrect() = runTest {
        //given
        val deleteForEveryone = true
        given(messageSender)
            .suspendFunction(messageSender::sendMessage)
            .whenInvokedWith(anything())
            .thenReturn(Either.Right(Unit))
        given(userRepository)
            .suspendFunction(userRepository::getSelfUser)
            .whenInvoked()
            .thenReturn(flowOf(TestUser.SELF))
        given(clientRepository)
            .function(clientRepository::currentClientId)
            .whenInvoked()
            .then { Either.Right(SELF_CLIENT_ID) }
        given(messageRepository)
            .suspendFunction(messageRepository::deleteMessage)
            .whenInvokedWith(anything(), anything())
            .thenReturn(Either.Right(Unit))


        //when
        val result = deleteMessageUseCase(TEST_CONVERSATION_ID, TEST_MESSAGE_UUID, deleteForEveryone).shouldSucceed()

        //then
        verify(messageSender)
            .suspendFunction(messageSender::sendMessage)
            .with(matching { message ->
                message.conversationId == TEST_CONVERSATION_ID && message.content == deletedMessageContent
            })
            .wasInvoked(exactly = once)
        verify(messageRepository)
            .suspendFunction(messageRepository::deleteMessage)
            .with(eq(TEST_MESSAGE_UUID), eq(TEST_CONVERSATION_ID))
            .wasInvoked(exactly = once)

        assertIs<Unit>(result)

    }

    @Test
    fun givenAMessage_WhenDeleteForEveryIsFalse_TheGeneratedMessageShouldBeCorrect() = runTest {
        //given
        val deleteForEveryone = false
        given(messageSender)
            .suspendFunction(messageSender::sendMessage)
            .whenInvokedWith(anything())
            .thenReturn(Either.Right(Unit))
        given(userRepository)
            .suspendFunction(userRepository::getSelfUser)
            .whenInvoked()
            .thenReturn(flowOf(TestUser.SELF))
        given(clientRepository)
            .function(clientRepository::currentClientId)
            .whenInvoked()
            .then { Either.Right(SELF_CLIENT_ID) }
        given(messageRepository)
            .suspendFunction(messageRepository::deleteMessage)
            .whenInvokedWith(anything(), anything())
            .thenReturn(Either.Right(Unit))

        //when
        val result = deleteMessageUseCase(TEST_CONVERSATION_ID, TEST_MESSAGE_UUID, deleteForEveryone).shouldSucceed()

        //then
        verify(messageSender)
            .suspendFunction(messageSender::sendMessage)
            .with(matching { message ->
                message.conversationId == TestUser.SELF.id && message.content == deletedForMeContent
            })
            .wasInvoked(exactly = once)

        verify(messageRepository)
            .suspendFunction(messageRepository::deleteMessage)
            .with(eq(TEST_MESSAGE_UUID), eq(TEST_CONVERSATION_ID))
            .wasInvoked(exactly = once)
        assertIs<Unit>(result)

    }


    companion object {
        val TEST_CONVERSATION_ID = TestConversation.ID
        const val TEST_MESSAGE_UUID = "messageUuid"
        const val TEST_TIME = "time"
        val TEST_CORE_FAILURE = Either.Left(CoreFailure.Unknown(Throwable("an error")))
        val SELF_CLIENT_ID: ClientId = PlainId("client_self")
        val deletedMessageContent = MessageContent.DeleteMessage(TEST_MESSAGE_UUID)
        val deletedForMeContent = MessageContent.DeleteForMe(TEST_MESSAGE_UUID, TEST_CONVERSATION_ID.value)

    }

}

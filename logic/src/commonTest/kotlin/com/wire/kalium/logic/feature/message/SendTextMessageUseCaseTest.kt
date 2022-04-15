package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.PlainId
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.SyncManager
import io.mockative.Mock
import io.mockative.Times
import io.mockative.anything
import io.mockative.given
import io.mockative.mock
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs

class SendTextMessageUseCaseTest {

    @Mock
    private val messageRepository = mock(MessageRepository::class)

    @Mock
    private val userRepository = mock(UserRepository::class)

    @Mock
    private val clientRepository = mock(ClientRepository::class)

    @Mock
    private val syncManager = mock(SyncManager::class)

    @Mock
    private val messageSender: MessageSender = mock(MessageSender::class)

    lateinit var sendTextMessageUseCase: SendTextMessageUseCase

    @BeforeTest
    fun setup() {
        sendTextMessageUseCase = SendTextMessageUseCase(
            messageRepository = messageRepository,
            userRepository = userRepository,
            clientRepository = clientRepository,
            syncManager = syncManager,
            messageSender = messageSender
        )
    }

    @Test
    fun givenRecipients_whenCreatingAnEnvelope_thenProteusClientShouldBeUsedToEncryptForEachClient() = runTest {
        //given
        given(syncManager)
            .suspendFunction(syncManager::waitForSlowSyncToComplete)
            .whenInvoked()
            .thenReturn(Unit)

        given(userRepository)
            .suspendFunction(userRepository::getSelfUser)
            .whenInvoked()
            .thenReturn(flowOf(TestUser.SELF))

        given(clientRepository)
            .suspendFunction(clientRepository::currentClientId)
            .whenInvoked()
            .thenReturn(Either.Left(CoreFailure.Unknown(IllegalArgumentException())))
        //when
        val result = sendTextMessageUseCase(ConversationId("test", "test"), "text")

        //then
        verify(messageRepository)
            .suspendFunction(messageRepository::persistMessage)
            .with(anything())
            .wasNotInvoked()

        verify(messageSender)
            .suspendFunction(messageSender::trySendingOutgoingMessageById)
            .with(anything(), anything())
            .wasNotInvoked()

        assertIs<SendTextMessageResult.Failure>(result)
    }

    @Test
    fun givenRecipients_whenCreatingAnEnvelope_thenProteusClientShouldBeUsedToEncryptForEachClient123() = runTest {
        //given
        given(syncManager)
            .suspendFunction(syncManager::waitForSlowSyncToComplete)
            .whenInvoked()
            .thenReturn(Unit)

        given(userRepository)
            .suspendFunction(userRepository::getSelfUser)
            .whenInvoked()
            .thenReturn(flowOf(TestUser.SELF))

        given(clientRepository)
            .suspendFunction(clientRepository::currentClientId)
            .whenInvoked()
            .thenReturn(Either.Right(PlainId("test")))

        given(messageRepository)
            .suspendFunction(messageRepository::persistMessage)
            .whenInvokedWith(anything())
            .thenReturn(
                Either.Left(CoreFailure.Unknown(IllegalArgumentException()))
            )

        given(messageRepository)
            .suspendFunction(messageRepository::updateMessage)
            .whenInvokedWith(anything())
            .thenReturn(
                Either.Left(CoreFailure.Unknown(IllegalArgumentException()))
            )
        //when
        val result = sendTextMessageUseCase(ConversationId("test", "test"), "text")

        //then
        verify(messageRepository)
            .suspendFunction(messageRepository::persistMessage)
            .with(anything())
            .wasInvoked(Times(1))

        verify(messageRepository)
            .suspendFunction(messageRepository::updateMessage)
            .with(anything())
            .wasInvoked(Times(1))

        verify(messageSender)
            .suspendFunction(messageSender::trySendingOutgoingMessageById)
            .with(anything(), anything())
            .wasNotInvoked()

        assertIs<SendTextMessageResult.Failure>(result)
    }

    @Test
    fun givenRecipients_whenCreatingAnEnvelope_thenProteusClientShouldBeUsedToEncryptForEachClient1234() = runTest {
        //given
        given(syncManager)
            .suspendFunction(syncManager::waitForSlowSyncToComplete)
            .whenInvoked()
            .thenReturn(Unit)

        given(userRepository)
            .suspendFunction(userRepository::getSelfUser)
            .whenInvoked()
            .thenReturn(flowOf(TestUser.SELF))

        given(clientRepository)
            .suspendFunction(clientRepository::currentClientId)
            .whenInvoked()
            .thenReturn(Either.Right(PlainId("test")))

        given(messageRepository)
            .suspendFunction(messageRepository::persistMessage)
            .whenInvokedWith(anything())
            .thenReturn(Either.Right(Unit))

        given(messageRepository)
            .suspendFunction(messageRepository::updateMessage)
            .whenInvokedWith(anything())
            .thenReturn(
                Either.Left(CoreFailure.Unknown(IllegalArgumentException()))
            )

        given(messageSender)
            .suspendFunction(messageSender::trySendingOutgoingMessageById)
            .whenInvokedWith(anything(), anything())
            .thenReturn(Either.Left(CoreFailure.Unknown(IllegalArgumentException())))
        //when
        val result = sendTextMessageUseCase(ConversationId("test", "test"), "text")

        //then
        verify(messageRepository)
            .suspendFunction(messageRepository::persistMessage)
            .with(anything())
            .wasInvoked(Times(1))

        verify(messageSender)
            .suspendFunction(messageSender::trySendingOutgoingMessageById)
            .with(anything(), anything())
            .wasInvoked(Times(1))

        assertIs<SendTextMessageResult.Failure>(result)
    }

    @Test
    fun givenRecipients_whenCreatingAnEnvelope_thenProteusClientShouldBeUsedToEncryptForEachClient12345() = runTest {
        //given
        given(syncManager)
            .suspendFunction(syncManager::waitForSlowSyncToComplete)
            .whenInvoked()
            .thenReturn(Unit)

        given(userRepository)
            .suspendFunction(userRepository::getSelfUser)
            .whenInvoked()
            .thenReturn(flowOf(TestUser.SELF))

        given(clientRepository)
            .suspendFunction(clientRepository::currentClientId)
            .whenInvoked()
            .thenReturn(Either.Right(PlainId("test")))

        given(messageRepository)
            .suspendFunction(messageRepository::persistMessage)
            .whenInvokedWith(anything())
            .thenReturn(Either.Right(Unit))

        given(messageRepository)
            .suspendFunction(messageRepository::updateMessage)
            .whenInvokedWith(anything())
            .thenReturn(Either.Right(Unit))

        given(messageSender)
            .suspendFunction(messageSender::trySendingOutgoingMessageById)
            .whenInvokedWith(anything(), anything())
            .thenReturn(Either.Right(Unit))
        //when
        val result = sendTextMessageUseCase(ConversationId("test", "test"), "text")

        //then
        verify(messageRepository)
            .suspendFunction(messageRepository::persistMessage)
            .with(anything())
            .wasInvoked(Times(1))

        verify(messageRepository)
            .suspendFunction(messageRepository::updateMessage)
            .with(anything())
            .wasInvoked(Times(1))

        verify(messageSender)
            .suspendFunction(messageSender::trySendingOutgoingMessageById)
            .with(anything(), anything())
            .wasInvoked(Times(1))

        assertIs<SendTextMessageResult.Success>(result)
    }

}

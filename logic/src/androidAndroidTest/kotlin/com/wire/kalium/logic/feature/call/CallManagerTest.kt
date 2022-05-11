package com.wire.kalium.logic.feature.call

import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.logic.data.call.CallMapper
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.message.MessageSender
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class CallManagerTest {

    @Mock
    private val calling = mock(classOf<Calling>())

    @Mock
    private val callRepository = mock(classOf<CallRepository>())

    @Mock
    private val userRepository = mock(classOf<UserRepository>())

    @Mock
    private val clientRepository = mock(classOf<ClientRepository>())

    @Mock
    private val messageSender = mock(classOf<MessageSender>())

    @Mock
    private val callMapper = mock(classOf<CallMapper>())

    private lateinit var callManagerImpl: CallManagerImpl

    @BeforeTest
    fun setUp() {
        callManagerImpl = CallManagerImpl(
            calling = calling,
            callRepository = callRepository,
            userRepository = userRepository,
            clientRepository = clientRepository,
            callMapper = callMapper,
            messageSender = messageSender
        )
    }

    @Test
    fun givenCallManager_whenCallingMessageIsReceived_then_wcall_recv_msg_IsCalled() = runTest {
        val baseHandle = Handle(value = 0)
        val expectedConversationId = "conversationId"

        callManagerImpl.onCallingMessageReceived(
            message = CALL_MESSAGE,
            content = CALL_CONTENT
        )

        verify(calling)
            .function(calling::wcall_recv_msg)
            .with(
                eq(baseHandle),
                eq(CALL_CONTENT.value.toByteArray()),
                eq(CALL_CONTENT.value.toByteArray().size),
                any(),
                any(),
                eq(expectedConversationId),
                eq(USER_ID.toString()),
                eq(CLIENT_ID.value)
            )
            .wasInvoked(exactly = once)
    }

    private companion object {
        val CLIENT_ID = ClientId(value = "clientId")
        val USER_ID = UserId(value = "userId", domain = "domainId")
        val CALL_CONTENT = MessageContent.Calling(value = "content")
        val CALL_MESSAGE = Message(
            id = "id",
            content = CALL_CONTENT,
            conversationId = ConversationId(value = "value", domain = "domain"),
            date = "2022-03-30T15:36:00.000Z",
            senderUserId = UserId(value = "value", domain = "domain"),
            senderClientId = ClientId(value = "value"),
            status = Message.Status.SENT
        )
    }
}

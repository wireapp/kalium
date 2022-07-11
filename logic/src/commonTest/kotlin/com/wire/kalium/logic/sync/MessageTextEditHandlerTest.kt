package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.handler.MessageTextEditHandler
import io.mockative.Mock
import io.mockative.anything
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class MessageTextEditHandlerTest {

    @Mock
    private val messageRepository: MessageRepository = mock(MessageRepository::class)

    private val messageTextEditHandler: MessageTextEditHandler = MessageTextEditHandler(messageRepository)

    @Test
    fun givenACorrectMessageAndMessageContent_whenHandeling_ThenDataGetsUpdatedCorrectly() = runTest {
        // given
        val mockMessage = Message.Regular(
            id = "someId",
            content = MessageContent.Text("some new content"),
            conversationId = ConversationId("someValue", "someDomain"),
            date = "someDate",
            senderUserId = UserId("someValue", "someDomain"),
            senderClientId = ClientId("someValue"),
            status = Message.Status.SENT,
            editStatus = Message.EditStatus.NotEdited,
            visibility = Message.Visibility.VISIBLE
        )

        val mockMessageContent = MessageContent.TextEdited(
            editMessageId = "someId",
            newContent = "some new content"
        )

        given(messageRepository)
            .suspendFunction(messageRepository::updateTextMessageContent)
            .whenInvokedWith(anything(), anything(), anything())
            .thenReturn(Either.Right(Unit))

        given(messageRepository)
            .suspendFunction(messageRepository::markMessageAsEdited)
            .whenInvokedWith(anything(), anything(), anything())
            .thenReturn(Either.Right(Unit))

        given(messageRepository)
            .suspendFunction(messageRepository::updateMessageId)
            .whenInvokedWith(anything(), anything(), anything())
            .thenReturn(Either.Right(Unit))
        // when
        messageTextEditHandler.handle(mockMessage, mockMessageContent)
        // then
        verify(messageRepository)
            .suspendFunction(messageRepository::updateTextMessageContent)
            .with(eq(mockMessage.conversationId), eq(mockMessageContent.editMessageId), eq(mockMessageContent.newContent))
            .wasInvoked(exactly = once)

        verify(messageRepository)
            .suspendFunction(messageRepository::markMessageAsEdited)
            .with(eq(mockMessageContent.editMessageId), eq(mockMessage.conversationId), eq(mockMessage.date))
            .wasInvoked(exactly = once)

        verify(messageRepository)
            .suspendFunction(messageRepository::updateMessageId)
            .with(eq(mockMessage.conversationId), eq(mockMessageContent.editMessageId), eq(mockMessage.id))
            .wasInvoked(exactly = once)
    }

}

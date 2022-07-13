package com.wire.kalium.logic.framework

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent

object TestMessage {
    const val TEST_MESSAGE_ID = "messageId"
    val TEST_SENDER_USER_ID = TestUser.USER_ID
    val TEST_SENDER_CLIENT_ID = TestClient.CLIENT_ID
    val TEXT_CONTENT = MessageContent.Text("Ciao!")
    val TEXT_MESSAGE = Message.Regular(
        id = TEST_MESSAGE_ID,
        content = TEXT_CONTENT,
        conversationId = ConversationId("conv", "id"),
        date = "date",
        senderUserId = TEST_SENDER_USER_ID,
        senderClientId = TEST_SENDER_CLIENT_ID,
        status = Message.Status.PENDING,
        editStatus = Message.EditStatus.NotEdited
    )
    val MISSED_CALL_MESSAGE = Message.System(
        id = TEST_MESSAGE_ID,
        content = MessageContent.MissedCall,
        conversationId = ConversationId("conv", "id"),
        date = "date",
        senderUserId = TEST_SENDER_USER_ID,
        status = Message.Status.PENDING,
    )
}

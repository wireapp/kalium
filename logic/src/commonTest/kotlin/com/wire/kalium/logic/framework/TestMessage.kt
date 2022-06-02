package com.wire.kalium.logic.framework

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent

object TestMessage {
    const val TEST_MESSAGE_ID = "messageId"
    val TEST_SENDER_USER_ID = TestUser.USER_ID
    val TEST_SENDER_CLIENT_ID = TestClient.CLIENT_ID
    val TEXT_CONTENT = MessageContent.Text("Ciao!")
    val TEXT_MESSAGE = Message.Client(
        TEST_MESSAGE_ID,
        TEXT_CONTENT,
        ConversationId("conv", "id"),
        "date",
        TEST_SENDER_USER_ID,
        TEST_SENDER_CLIENT_ID,
        Message.Status.PENDING,
        Message.EditStatus.NotEdited
    )
}

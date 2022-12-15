package com.wire.kalium.logic.framework

import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientType
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId

object TestClient {
    val CLIENT_ID = ClientId("test")

    val CLIENT = Client(
        CLIENT_ID,
        ClientType.Permanent,
        "time",
        null,
        null,
        "label",
        "cookie",
        null,
        "model",
        emptyMap()
    )

    val SELF_USER_ID = UserId("self-user-id", "domain")
    val CONVERSATION_ID = ConversationId("conversation-id", "domain")
    val USER_ID = UserId("client-id", "domain")
}

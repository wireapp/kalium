package com.wire.kalium.api.tools.json.api.notification

import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.api.tools.json.api.conversation.ConversationResponseJson
import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.notification.EventContentDTO

object EventContentDTOJson {

    private val jsonProvider = { serializable: EventContentDTO.Conversation.AccessUpdate ->
        """
        |{
        |  "qualified_conversation" : {
        |    "id" : "${serializable.qualifiedConversation.value}",
        |    "domain" : "${serializable.qualifiedConversation.domain}"
        |  },
        |  "qualified_from" : {
        |     "id" : "${serializable.qualifiedFrom.value}",
        |     "domain" : "${serializable.qualifiedFrom.domain}"
        |  }, 
        |  "data" : ${ConversationResponseJson.conversationResponseSerializer(serializable.data)}
        |}
        """.trimMargin()
    }

    val valid = ValidJsonProvider(
        EventContentDTO.Conversation.AccessUpdate(
            qualifiedConversation = ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"),
            qualifiedFrom = UserId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"),
            data = ConversationResponseJson.validGroup.serializableData
        ), jsonProvider
    )
}

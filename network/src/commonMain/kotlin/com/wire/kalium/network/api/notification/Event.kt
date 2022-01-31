package com.wire.kalium.network.api.notification

import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class Event {

    @Serializable
    sealed class Conversation : Event() {
        @SerialName("qualified_conversation")
        abstract val qualifiedConversation: ConversationId

        @Serializable
        @SerialName("conversation.otr-message-add")
        data class NewMessage(
            override val qualifiedConversation: ConversationId,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
            val time: String,
            @SerialName("data") val data: MessageEventData,
        ) : Conversation()
    }

    @Serializable
    sealed class User : Event() {

        @SerialName("user.client-add")
        data class NewClient(
            @SerialName("client") val client: NewClientEventData,
        ) : User()
    }
}

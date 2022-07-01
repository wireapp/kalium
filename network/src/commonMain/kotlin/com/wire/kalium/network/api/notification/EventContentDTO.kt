package com.wire.kalium.network.api.notification

import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.conversation.ConversationMembers
import com.wire.kalium.network.api.conversation.ConversationResponse
import com.wire.kalium.network.api.conversation.ConversationUsers
import com.wire.kalium.network.api.notification.conversation.MessageEventData
import com.wire.kalium.network.api.notification.user.NewClientEventData
import com.wire.kalium.network.api.user.connection.ConnectionDTO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EventResponse(
    @SerialName("id") val id: String,
    @SerialName("payload") val payload: List<EventContentDTO>?,
    @SerialName("transient") val transient: Boolean = false
)

@Serializable
sealed class EventContentDTO {

    @Serializable
    sealed class Conversation : EventContentDTO() {

        @Serializable
        @SerialName("conversation.create")
        data class NewConversationDTO(
            @SerialName("qualified_conversation")
            val qualifiedConversation: ConversationId,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
            val time: String,
            @SerialName("data") val data: ConversationResponse,
        ) : Conversation()

        @Serializable
        @SerialName("conversation.otr-message-add")
        data class NewMessageDTO(
            @SerialName("qualified_conversation")
            val qualifiedConversation: ConversationId,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
            val time: String,
            @SerialName("data") val data: MessageEventData,
        ) : Conversation()

        @Serializable
        @SerialName("conversation.member-join")
        data class MemberJoinDTO(
            @SerialName("qualified_conversation")
            val qualifiedConversation: ConversationId,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
            val time: String,
            @SerialName("data") val members: ConversationMembers,
            @Deprecated("use qualifiedFrom", replaceWith = ReplaceWith("this.qualifiedFrom"))
            @SerialName("from") val from: String
        ) : Conversation()

        @Serializable
        @SerialName("conversation.member-leave")
        data class MemberLeaveDTO(
            @SerialName("qualified_conversation")
            val qualifiedConversation: ConversationId,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
            val time: String,
            @SerialName("data") val members: ConversationUsers,
            @SerialName("from") val from: String
        ) : Conversation()

        @Serializable
        @SerialName("conversation.mls-welcome")
        data class MLSWelcomeDTO(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
            @SerialName("data") val message: String,
            @SerialName("from") val from: String
        ) : Conversation()

        @Serializable
        @SerialName("conversation.mls-message-add")
        data class NewMLSMessageDTO(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
            val time: String,
            @SerialName("data") val message: String,
        ) : Conversation()
    }

    @Serializable
    sealed class User : EventContentDTO() {

        @Serializable
        @SerialName("user.client-add")
        data class NewClientDTO(
            @SerialName("client") val client: NewClientEventData,
        ) : User()

        @Serializable
        @SerialName("user.connection")
        data class NewConnectionDTO(
            @SerialName("connection") val connection: ConnectionDTO,
        ) : User()
    }

    @Serializable
    @SerialName("unknown")
    object Unknown : EventContentDTO()
}

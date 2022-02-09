package com.wire.kalium.network.api.notification

import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.user.client.EventClientResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EventResponse(
    @SerialName("id") val id: String,
    @SerialName("transient") val transient: Boolean,
    @SerialName("payload") val payload: List<Payload>?
)

@Serializable
data class Payload(
    @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
    @SerialName("conversation") val conversation: String,
    @SerialName("time") val time: String,
    @SerialName("data") val data: Data?,
    @SerialName("from") val from: String,
    @SerialName("qualified_from") val qualifiedFrom: UserId,
    @SerialName("type") val type: String,
    @SerialName("client") val client: EventClientResponse? = null
)

@Serializable
data class Data(
    @SerialName("text") val text: String,
    @SerialName("sender") val sender: String,
    @SerialName("recipient") val recipient: String
)

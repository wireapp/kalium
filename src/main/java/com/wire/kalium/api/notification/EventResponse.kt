package com.wire.kalium.api.notification

import com.wire.kalium.api.user.client.ClientResponse
import com.wire.kalium.models.backend.ConversationId
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
    @SerialName("qualified_from") val qualifiedFrom: QualifiedFrom,
    @SerialName("type") val type: String,
    @SerialName("client") val client: ClientResponse
)

@Serializable
data class Data(
    @SerialName("text") val text: String,
    @SerialName("sender") val sender: String,
    @SerialName("recipient") val recipient: String
)

@Serializable
data class QualifiedFrom(
    @SerialName("domain") val domain: String,
    @SerialName("id") val id: String
)

package com.wire.kalium.network.api.user.connection

import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConnectionResponse(
    @SerialName("connections") val connections: List<ConnectionDTO>,
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("paging_state") val pagingState: String
)

@Serializable
data class ConnectionDTO(
    @SerialName("conversation") val conversationId: String,
    @SerialName("from") val from: String,
    @SerialName("last_update") val lastUpdate: String,
    @SerialName("qualified_conversation") val qualifiedConversationId: ConversationId,
    @SerialName("qualified_to") val qualifiedToId: UserId,
    @SerialName("status") val status: ConnectionStateDTO,
    @SerialName("to") val toId: String
)

@Serializable
enum class ConnectionStateDTO {
    /** The other user has sent a connection request to this one */
    @SerialName("pending")
    PENDING,

    /** This user has sent a connection request to another user */
    @SerialName("sent")
    SENT,

    /** The user has been blocked */
    @SerialName("blocked")
    BLOCKED,

    /** The connection has been ignored */
    @SerialName("ignored")
    IGNORED,

    /** The connection has been cancelled */
    @SerialName("cancelled")
    CANCELLED,

    /** The connection is missing legal hold consent  */
    @SerialName("missing-legalhold-consent")
    MISSING_LEGALHOLD_CONSENT,

    /** The connection is complete and the conversation is in its normal state */
    @SerialName("accepted")
    ACCEPTED
}

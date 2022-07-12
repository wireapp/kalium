package com.wire.kalium.network.api.conversation.model

import com.wire.kalium.network.api.model.ConversationAccess
import com.wire.kalium.network.api.model.ConversationAccessRole
import com.wire.kalium.network.api.notification.EventContentDTO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConversationAccessData(
    @SerialName("access") val access: Set<ConversationAccess>,
    @SerialName("access_role_v2") val accessRole: Set<ConversationAccessRole>?
)

sealed class UpdateConversationAccessResponse {
    object AccessUnchanged : UpdateConversationAccessResponse()
    data class AccessUpdated(val event: EventContentDTO.Conversation.AccessUpdate) : UpdateConversationAccessResponse()
}

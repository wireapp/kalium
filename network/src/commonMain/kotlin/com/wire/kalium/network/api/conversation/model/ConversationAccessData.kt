package com.wire.kalium.network.api.conversation.model

import com.wire.kalium.network.api.model.ConversationAccessDTO
import com.wire.kalium.network.api.model.ConversationAccessRoleDTO
import com.wire.kalium.network.api.notification.EventContentDTO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConversationAccessData(
    @SerialName("access") val access: Set<ConversationAccessDTO>,
    @SerialName("access_role_v2") val accessRole: Set<ConversationAccessRoleDTO>?
)

sealed class UpdateConversationAccessResponse {
    object AccessUnchanged : UpdateConversationAccessResponse()
    data class AccessUpdated(val event: EventContentDTO.Conversation.AccessUpdate) : UpdateConversationAccessResponse()
}

package com.wire.kalium.network.api.base.authenticated.conversation.model

import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.model.ConversationAccessDTO
import com.wire.kalium.network.api.base.model.ConversationAccessRoleDTO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConversationAccessInfoDTO(
    @SerialName("access") val access: Set<ConversationAccessDTO>,
    @SerialName("access_role_v2") val accessRole: Set<ConversationAccessRoleDTO> = ConversationAccessRoleDTO.DEFAULT_VALUE_WHEN_NULL
)

sealed class UpdateConversationAccessResponse {
    object AccessUnchanged : UpdateConversationAccessResponse()
    data class AccessUpdated(val event: EventContentDTO.Conversation.AccessUpdate) : UpdateConversationAccessResponse()
}
